/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.repository.p4;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import ru.bozaro.p4.proto.Message;
import svnserver.repository.VcsCopyFrom;
import svnserver.repository.VcsFile;
import svnserver.repository.VcsLogEntry;
import svnserver.repository.VcsRevision;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * P4 revision.
 *
 * @author Artem V. Navrotskiy
 */
public class P4Revision implements VcsRevision {
  private final int id;
  private final long date;
  @Nullable
  private final String desc;
  @Nullable
  private final String author;

  public P4Revision(int id, @Nullable String author, @Nullable String desc, long date) {
    this.id = id;
    this.desc = desc;
    this.author = author;
    this.date = date;
  }

  public P4Revision(@NotNull Message message) {
    this.desc = message.getString("desc");
    this.id = Integer.valueOf(message.getString("change"));
    this.date = Long.valueOf(message.getString("time")).longValue() * 1000L;
    this.author = message.getString("user");
  }

  @Override
  public int getId() {
    return id;
  }

  @NotNull
  @Override
  public Map<String, String> getProperties(boolean includeInternalProps) {
    final Map<String, String> props = new HashMap<>();
    if (includeInternalProps) {
      putProperty(props, SVNRevisionProperty.AUTHOR, getAuthor());
      putProperty(props, SVNRevisionProperty.LOG, getLog());
      putProperty(props, SVNRevisionProperty.DATE, getDateString());
    }
    return props;
  }

  @Override
  public long getDate() {
    return date;
  }

  @Override
  @Nullable
  public String getAuthor() {
    return author;
  }

  @Override
  @Nullable
  public String getLog() {
    return desc;
  }

  @Override
  @Nullable
  public VcsFile getFile(@NotNull String fullPath) throws IOException, SVNException {
    return null;
  }

  @NotNull
  @Override
  public Map<String, ? extends VcsLogEntry> getChanges() throws IOException, SVNException {
    return null;
  }

  @Override
  @Nullable
  public VcsCopyFrom getCopyFrom(@NotNull String fullPath) {
    return null;
  }

  private void putProperty(@NotNull Map<String, String> props, @NotNull String name, @Nullable String value) {
    if (value != null) {
      props.put(name, value);
    }
  }
}
