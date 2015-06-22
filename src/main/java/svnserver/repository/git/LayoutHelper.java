/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import svnserver.repository.git.cache.CacheHelper;
import svnserver.repository.git.cache.CacheRevision;
import svnserver.repository.git.layout.RefMappingDirect;
import svnserver.repository.git.layout.RefMappingGroup;
import svnserver.repository.git.layout.RefMappingPrefix;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper for creating svn layout in git repository.
 *
 * @author a.navrotskiy
 */
public class LayoutHelper {
  @NotNull
  private static final RefMappingGroup layoutMapping = new RefMappingGroup(
      new RefMappingDirect(Constants.R_HEADS + Constants.MASTER, "trunk/"),
      new RefMappingPrefix(Constants.R_HEADS, "branches/"),
      new RefMappingPrefix(Constants.R_TAGS, "tags/")
  );
  @NotNull
  private static final String ENTRY_COMMIT_YML = "commit.yml";
  @NotNull
  private static final String ENTRY_COMMIT_REF = "commit.ref";
  @NotNull
  private static final String ENTRY_ROOT = "svn";
  @NotNull
  private static final String ENTRY_UUID = "uuid";

  @NotNull
  public static Ref initRepository(@NotNull Repository repository, @NotNull String svnRef) throws IOException {
    Ref ref = repository.getRef(svnRef);
    if (ref == null) {
      final ObjectId revision = createFirstRevision(repository);
      final RefUpdate refUpdate = repository.updateRef(svnRef);
      refUpdate.setNewObjectId(revision);
      refUpdate.update();
      ref = repository.getRef(svnRef);
      if (ref == null) {
        throw new IOException("Can't initialize repository.");
      }
    }
    return ref;
  }

  public static void resetCache(@NotNull Repository repository, @NotNull String svnRef) throws IOException {
    final Ref ref = repository.getRef(svnRef);
    if (ref != null) {
      final RefUpdate refUpdate = repository.updateRef(svnRef);
      refUpdate.setForceUpdate(true);
      refUpdate.delete();
    }
  }

  /**
   * Get active branches with commits from repository.
   *
   * @param repository Repository.
   * @return Branches with commits.
   * @throws IOException
   */
  public static Map<String, RevCommit> getBranches(@NotNull Repository repository) throws IOException {
    final RevWalk revWalk = new RevWalk(repository);
    final Map<String, RevCommit> result = new TreeMap<>();
    for (Ref ref : repository.getAllRefs().values()) {
      try {
        final String svnPath = layoutMapping.gitToSvn(ref.getName());
        if (svnPath != null) {
          final RevCommit revCommit = unwrapCommit(revWalk, ref.getObjectId());
          if (revCommit != null) {
            result.put(svnPath, revCommit);
          }
        }
      } catch (MissingObjectException ignored) {
      }
    }
    return result;
  }

  /**
   * Load all new revision from repository and update repository graph.
   *
   * @param repository   Git repository.
   * @param graph        Revision graph.
   * @param newRevisions Collections with added revision.
   * @param <T>          Revisions type.
   * @return Return newRevisions.
   * @throws IOException
   * @apiNote Child revisions always added to newRevisions collection after parent revision.
   */
  @Contract("_, _, _, null -> null; _, _, _, !null -> !null")
  public static <T extends Collection<ObjectId>> T loadRevisionGraph(@NotNull Repository repository, @NotNull Collection<? extends ObjectId> heads, @NotNull DirectedGraph<ObjectId, DefaultEdge> graph, @Nullable T newRevisions) throws IOException {
    final Deque<ObjectId> queue = new ArrayDeque<>();
    final DirectedGraph<ObjectId, DefaultEdge> added = new SimpleDirectedGraph<>(DefaultEdge.class);
    RevWalk revWalk = new RevWalk(repository);
    for (ObjectId commit : heads) {
      final ObjectId commitId = commit.toObjectId();
      if (graph.addVertex(commitId)) {
        queue.add(commitId);
        added.addVertex(commitId);
      }
    }
    while (true) {
      final ObjectId id = queue.pollLast();
      if (id == null) {
        break;
      }
      final RevCommit commit = revWalk.parseCommit(id);
      graph.addVertex(commit);
      for (RevCommit parent : commit.getParents()) {
        final ObjectId parentId = parent.toObjectId();
        if (graph.addVertex(parentId)) {
          added.addVertex(parentId);
          queue.add(parent);
        }
        if (added.containsVertex(parentId)) {
          added.addEdge(parentId, id);
        }
        graph.addEdge(parentId, id);
      }
    }
    if (newRevisions != null) {
      // Create new revisions list in right order.
      for (ObjectId id : heads) {
        if (added.outgoingEdgesOf(id).isEmpty() && !queue.contains(id)) {
          queue.push(id);
        }
      }
      while (!queue.isEmpty()) {
        final ObjectId id = queue.pop();
        if (!added.containsVertex(id)) {
          continue;
        }
        final Set<DefaultEdge> edges = added.incomingEdgesOf(id);
        if (!edges.isEmpty()) {
          queue.push(id);
          for (DefaultEdge edge : edges) {
            queue.push(added.getEdgeSource(edge));
          }
          added.removeAllEdges(new HashSet<>(edges));
        } else {
          added.removeVertex(id);
          newRevisions.add(id);
        }
      }
    }
    return newRevisions;
  }

  public static ObjectId createCacheCommit(@NotNull ObjectInserter inserter, @NotNull ObjectId parent, @NotNull RevCommit commit, @NotNull CacheRevision cacheRevision) throws IOException {
    final TreeFormatter treeBuilder = new TreeFormatter();
    treeBuilder.append(ENTRY_COMMIT_REF, commit);
    treeBuilder.append(ENTRY_COMMIT_YML, FileMode.REGULAR_FILE, CacheHelper.save(inserter, cacheRevision));
    treeBuilder.append("svn", FileMode.TREE, createSvnLayoutTree(inserter, cacheRevision.getBranches()));

    new ObjectChecker().checkTree(treeBuilder.toByteArray());
    final ObjectId rootTree = inserter.insert(treeBuilder);

    final CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(commit.getAuthorIdent());
    commitBuilder.setCommitter(commit.getCommitterIdent());
    commitBuilder.setMessage("#" + cacheRevision.getRevisionId() + ": " + commit.getFullMessage());
    commitBuilder.addParentId(parent);
    commitBuilder.setTreeId(rootTree);
    return inserter.insert(commitBuilder);
  }

  /**
   * Unwrap commit from reference.
   *
   * @param revWalk  Git parser.
   * @param objectId Reference object.
   * @return Wrapped commit or null (ex: tag on tree).
   * @throws IOException .
   */
  @Nullable
  private static RevCommit unwrapCommit(@NotNull RevWalk revWalk, @NotNull ObjectId objectId) throws IOException {
    RevObject revObject = revWalk.parseAny(objectId);
    while (true) {
      if (revObject instanceof RevCommit) {
        return (RevCommit) revObject;
      }
      if (revObject instanceof RevTag) {
        revObject = ((RevTag) revObject).getObject();
        continue;
      }
      return null;
    }
  }

  @NotNull
  public static CacheRevision loadCacheRevision(@NotNull ObjectReader objectReader, @NotNull RevCommit commit) throws IOException {
    return CacheHelper.load(TreeWalk.forPath(objectReader, ENTRY_COMMIT_YML, commit.getTree()));
  }

  @NotNull
  public static String loadRepositoryId(@NotNull ObjectReader objectReader, ObjectId commit) throws IOException {
    RevWalk revWalk = new RevWalk(objectReader);
    TreeWalk treeWalk = TreeWalk.forPath(objectReader, ENTRY_UUID, revWalk.parseCommit(commit).getTree());
    if (treeWalk != null) {
      return new String(objectReader.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
    }
    throw new FileNotFoundException(ENTRY_UUID);
  }

  @NotNull
  private static ObjectId createFirstRevision(@NotNull Repository repository) throws IOException {
    // Generate UUID.
    final ObjectInserter inserter = repository.newObjectInserter();
    ObjectId uuidId = inserter.insert(Constants.OBJ_BLOB, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    // Create svn empty tree.
    final ObjectId treeId = inserter.insert(new TreeFormatter());
    // Create commit tree.
    final TreeFormatter rootBuilder = new TreeFormatter();
    rootBuilder.append(ENTRY_ROOT, FileMode.TREE, treeId);
    rootBuilder.append(ENTRY_UUID, FileMode.REGULAR_FILE, uuidId);
    new ObjectChecker().checkTree(rootBuilder.toByteArray());
    final ObjectId rootId = inserter.insert(rootBuilder);
    // Create first commit with message.
    final CommitBuilder commitBuilder = new CommitBuilder();
    commitBuilder.setAuthor(new PersonIdent("", "", 0, 0));
    commitBuilder.setCommitter(new PersonIdent("", "", 0, 0));
    commitBuilder.setMessage("#0: Initial revision");
    commitBuilder.setTreeId(rootId);
    final ObjectId commitId = inserter.insert(commitBuilder);
    inserter.flush();
    return commitId;
  }

  @Nullable
  private static ObjectId createSvnLayoutTree(@NotNull ObjectInserter inserter, @NotNull Map<String, ObjectId> revBranches) throws IOException {
    final Deque<TreeFormatter> stack = new ArrayDeque<>();
    stack.add(new TreeFormatter());
    String dir = "";
    final ObjectChecker checker = new ObjectChecker();
    for (Map.Entry<String, ObjectId> entry : new TreeMap<>(revBranches).entrySet()) {
      final String path = entry.getKey();
      // Save already added nodes.
      while (!path.startsWith(dir)) {
        final int index = dir.lastIndexOf('/', dir.length() - 2) + 1;
        final TreeFormatter tree = stack.pop();
        checker.checkTree(tree.toByteArray());
        stack.element().append(dir.substring(index, dir.length() - 1), FileMode.TREE, inserter.insert(tree));
        dir = dir.substring(0, index);
      }
      // Go deeper.
      for (int index = path.indexOf('/', dir.length()) + 1; index < path.length(); index = path.indexOf('/', index) + 1) {
        dir = path.substring(0, index);
        stack.push(new TreeFormatter());
      }
      // Add commit to tree.
      {
        final int index = path.lastIndexOf('/', path.length() - 2) + 1;
        stack.element().append(path.substring(index, path.length() - 1), FileMode.GITLINK, entry.getValue());
      }
    }
    // Save already added nodes.
    while (!dir.isEmpty()) {
      int index = dir.lastIndexOf('/', dir.length() - 2) + 1;
      final TreeFormatter tree = stack.pop();
      checker.checkTree(tree.toByteArray());
      stack.element().append(dir.substring(index, dir.length() - 1), FileMode.TREE, inserter.insert(tree));
      dir = dir.substring(0, index);
    }
    // Save root tree to disk.
    final TreeFormatter rootTree = stack.pop();
    checker.checkTree(rootTree.toByteArray());
    if (!stack.isEmpty()) {
      throw new IllegalStateException();
    }
    return inserter.insert(rootTree);
  }

  /**
   * Sort revisions by date.
   *
   * @param revisions  Partially sorted revisions (child revisions always after parent revisions).
   * @param repository Repository.
   * @return Revision sorted by date, but child revisions always after parent revisions.
   */
  @NotNull
  public static List<ObjectId> sortRevision(@NotNull Repository repository, @NotNull Collection<ObjectId> revisions, @NotNull Comparator<RevCommit> comparator) throws IOException {
    final RevWalk revWalk = new RevWalk(repository);
    final List<RevCommit> commits = new ArrayList<>();
    for (ObjectId objectId : revisions) {
      commits.add(revWalk.parseCommit(objectId));
    }

    int maxIndex = 1;
    while (maxIndex < commits.size() - 1) {
      RevCommit maxRev = commits.get(maxIndex);
      int minIndex = maxIndex;
      for (int index = maxIndex - 1; index >= 0; index--) {
        RevCommit minRev = commits.get(index);
        if (isParentOf(minRev, maxRev)) {
          break;
        }
        if (comparator.compare(minRev, maxRev) > 0) {
          minIndex = index;
        }
      }
      for (int index = maxIndex - 1; index >= minIndex; index--) {
        RevCommit a = commits.get(index + 1);
        RevCommit b = commits.get(index);
        commits.set(index, a);
        commits.set(index + 1, b);
      }
      maxIndex = minIndex + 1;
    }

    return commits.stream().map(RevCommit::getId).collect(Collectors.toList());
  }

  private static boolean isParentOf(@NotNull RevCommit parent, @NotNull RevCommit child) {
    for (RevCommit item : parent.getParents()) {
      if (item.equals(child)) {
        return true;
      }
    }
    return false;
  }
}
