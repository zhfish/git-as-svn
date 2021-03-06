/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.mapping;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import svnserver.StringHelper;
import svnserver.repository.RepositoryInfo;
import svnserver.repository.VcsRepository;
import svnserver.repository.VcsRepositoryMapping;

import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Simple repository mapping by predefined list.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public class RepositoryListMapping implements VcsRepositoryMapping {
  @NotNull
  private final NavigableMap<String, VcsRepository> mapping;
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(RepositoryListMapping.class);

  public RepositoryListMapping(@NotNull Map<String, VcsRepository> mapping) {
    this.mapping = new TreeMap<>(mapping);
  }

  @Nullable
  @Override
  public RepositoryInfo getRepository(@NotNull SVNURL url) throws SVNException {
    final Map.Entry<String, VcsRepository> entry = getMapped(mapping, url.getPath());
    if (entry != null) {
      return new RepositoryInfo(
          SVNURL.create(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort() == SVNURL.getDefaultPortNumber(url.getProtocol()) ? -1 : url.getPort(), entry.getKey(), true),
          entry.getValue()
      );
    }
    return null;
  }

  @Override
  public void initRevisions() throws IOException, SVNException {
    for (Map.Entry<String, VcsRepository> entry : mapping.entrySet()) {
      log.info("Repository initialize: {}", entry.getKey());
      entry.getValue().updateRevisions();
    }
  }

  @Nullable
  public static <T> Map.Entry<String, T> getMapped(@NotNull NavigableMap<String, T> mapping, @NotNull String prefix) {
    final String path = StringHelper.normalizeDir(prefix);
    final Map.Entry<String, T> entry = mapping.floorEntry(path);
    if (entry != null && StringHelper.isParentPath(entry.getKey(), path)) {
      return entry;
    }
    return null;
  }

  public static RepositoryListMapping create(@NotNull String prefix, @NotNull VcsRepository repository) {
    return new Builder()
        .add(prefix, repository)
        .build();
  }

  public static class Builder {
    @NotNull
    private final Map<String, VcsRepository> mapping = new TreeMap<>();

    public Builder add(@NotNull String prefix, @NotNull VcsRepository repository) {
      mapping.put(StringHelper.normalizeDir(prefix), repository);
      return this;
    }

    public RepositoryListMapping build() {
      return new RepositoryListMapping(mapping);
    }
  }
}
