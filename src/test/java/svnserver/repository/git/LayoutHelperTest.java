/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.testng.Assert;
import org.testng.annotations.Test;
import svnserver.SvnTestServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for layout helper.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class LayoutHelperTest {
  @Test
  public void loadRevisionGraphTest() throws Exception {
    try (SvnTestServer master = SvnTestServer.createMasterRepository()) {
      final Repository repository = master.getRepository();
      final DirectedGraph<ObjectId, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
      final ArrayList<ObjectId> newRevisions = LayoutHelper.loadRevisionGraph(repository, LayoutHelper.getBranches(repository).values(), graph, new ArrayList<>());
      Assert.assertEquals(newRevisions.size(), graph.vertexSet().size());

      final RevWalk revWalk = new RevWalk(repository);
      final Set<ObjectId> competed = new HashSet<>();
      for (ObjectId id : newRevisions) {
        final RevCommit commit = revWalk.parseCommit(id);
        if (commit != null) {
          for (RevCommit parent : commit.getParents()) {
            Assert.assertTrue(competed.contains(parent.getId()), "Invalid commit order: " + commit.getId().abbreviate(7).name() + " -> " + parent.getId().abbreviate(7).name());
          }
        }
        competed.add(id);
      }
    }
  }
}
