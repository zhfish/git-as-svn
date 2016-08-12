/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.config;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import svnserver.config.serializer.ConfigType;
import svnserver.context.LocalContext;
import svnserver.repository.VcsRepository;
import svnserver.repository.p4.P4Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
@SuppressWarnings("FieldCanBeLocal")
@ConfigType("p4")
public final class P4RepositoryConfig implements RepositoryConfig {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(P4RepositoryConfig.class);
  @NotNull
  private String branch = "master";
  @NotNull
  private String path = ".git";
  @NotNull
  private List<LocalConfig> extensions = new ArrayList<>();

  private boolean renameDetection = true;

  @NotNull
  @Override
  public VcsRepository create(@NotNull LocalContext context) throws IOException, SVNException {
    P4Repository repository = new P4Repository(context);
    for (LocalConfig extension : extensions) {
      extension.create(context);
    }
    return repository;
  }
}
