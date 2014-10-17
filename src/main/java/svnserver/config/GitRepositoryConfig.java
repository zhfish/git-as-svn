/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.serializer.ConfigType;
import svnserver.repository.VcsRepository;
import svnserver.repository.git.GitCreateMode;
import svnserver.repository.git.GitPushMode;
import svnserver.repository.git.GitRepository;
import svnserver.repository.git.LayoutHelper;
import svnserver.repository.locks.PersistentLockFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("git")
public final class GitRepositoryConfig implements RepositoryConfig {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(GitRepositoryConfig.class);
  @NotNull
  @Deprecated
  private String branch = "master";
  @NotNull
  private String path = ".git";
  @SuppressWarnings("MismatchedReadAndWriteOfArray")
  @NotNull
  private String[] submodules = {};
  @NotNull
  private GitPushMode pushMode = GitPushMode.NATIVE;
  @NotNull
  private GitCreateMode createMode = GitCreateMode.ERROR;
  private boolean renameDetection = true;
  private boolean resetCache = false;

  @NotNull
  public Repository createRepository(@NotNull File basePath) throws IOException {
    final File fullPath = ConfigHelper.joinPath(basePath, path);
    if (!fullPath.exists()) {
      log.info("Repository fullPath: {} - not exists, create mode: {}", fullPath, createMode);
      return createMode.createRepository(fullPath, branch);
    }
    log.info("Repository fullPath: {}", fullPath);
    return new FileRepository(fullPath);
  }

  @NotNull
  public List<Repository> createLinkedRepositories() throws IOException {
    final List<Repository> result = new ArrayList<>();
    for (String linkedPath : submodules) {
      final File file = new File(linkedPath).getAbsoluteFile();
      if (!file.exists()) {
        throw new FileNotFoundException(file.getPath());
      }
      log.info("Linked repository path: {}", file);
      final FileRepository linkedRepository = new FileRepository(file);
      result.add(linkedRepository);
    }
    return result;
  }

  @NotNull
  @Override
  public VcsRepository create(@NotNull File basePath, @NotNull DB cacheDb) throws IOException, SVNException {
    final Repository repo = createRepository(basePath);
    if (resetCache) {
      log.warn("Clear repository cache");
      LayoutHelper.resetCache(repo);
    }
    return new GitRepository(repo, createLinkedRepositories(), pushMode, branch, renameDetection, new PersistentLockFactory(cacheDb), cacheDb);
  }
}
