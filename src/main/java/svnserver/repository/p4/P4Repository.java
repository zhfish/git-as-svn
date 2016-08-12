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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.svn.core.SVNException;
import ru.bozaro.p4.proto.Client;
import ru.bozaro.p4.proto.Message;
import svnserver.auth.User;
import svnserver.context.LocalContext;
import svnserver.repository.VcsRepository;
import svnserver.repository.VcsRevision;
import svnserver.repository.VcsWriter;
import svnserver.repository.locks.LockManagerRead;
import svnserver.repository.locks.LockManagerWrite;
import svnserver.repository.locks.LockWorker;

import java.io.IOException;
import java.net.Socket;

/**
 * Implementation for P4 repository.
 *
 * @author Artem V. Navrotskiy
 */
public class P4Repository implements VcsRepository {
  @NotNull
  private static final Logger log = LoggerFactory.getLogger(P4Repository.class);
  @NotNull
  private final LocalContext context;
  @NotNull
  private final Client client;

  public P4Repository(@NotNull LocalContext context) throws IOException, SVNException {
    this.context = context;
    this.client = new Client(new Socket("100.112.6.208", 1666), (prompt, noecho) -> "secret", null);
    log.info("Repository registered");
  }

  @NotNull
  @Override
  public String getUuid() {
    return "xxx";
  }

  @NotNull
  @Override
  public LocalContext getContext() {
    return context;
  }

  @NotNull
  @Override
  public VcsRevision getLatestRevision() throws IOException {
    RevisionParser parser = new RevisionParser();
    client.p4(parser, "changes", "-m", "1", "-l", "-t");
    return parser.revision;
  }

  @NotNull
  @Override
  public VcsRevision getRevisionByDate(long dateTime) throws IOException {
    return null;
  }

  @Override
  public void updateRevisions() throws IOException, SVNException {

  }

  @NotNull
  @Override
  public VcsRevision getRevisionInfo(int revision) throws IOException, SVNException {
    if (revision == 0) {
      return new P4Revision(revision, null, null, 0L);
    }
    RevisionParser parser = new RevisionParser();
    client.p4(parser, "describe", "-s", "-m", "1", String.valueOf(revision));
    return parser.revision;
  }

  @Override
  public int getLastChange(@NotNull String nodePath, int beforeRevision) {
    return 0;
  }

  @NotNull
  @Override
  public <T> T wrapLockRead(@NotNull LockWorker<T, LockManagerRead> work) throws SVNException, IOException {
    return null;
  }

  @NotNull
  @Override
  public <T> T wrapLockWrite(@NotNull LockWorker<T, LockManagerWrite> work) throws SVNException, IOException {
    return null;
  }

  @NotNull
  @Override
  public VcsWriter createWriter(@NotNull User user) throws SVNException, IOException {
    return null;
  }

  @Override
  public void close() throws IOException {

  }

  private static class RevisionParser implements Client.Callback {
    private P4Revision revision;

    @Override
    @Nullable
    public Message.Builder exec(@NotNull Message message) {
      if ("client-FstatInfo".equals(message.getFunc())) {
        revision = new P4Revision(message);
      }
      return null;
    }
  }
}
