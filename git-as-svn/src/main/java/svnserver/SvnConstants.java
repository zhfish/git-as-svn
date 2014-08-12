package svnserver;

import org.jetbrains.annotations.NotNull;

/**
 * Some svn constants.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 */
public final class SvnConstants {
  @NotNull
  public static final String PROP_AUTHOR = "svn:author";
  @NotNull
  public static final String PROP_DATE = "svn:date";
  @NotNull
  public static final String PROP_LOG = "svn:log";
  @NotNull
  public static final String PROP_EXEC = "svn:executable";
  @NotNull
  public static final String PROP_GIT = "git:sha1";

  @NotNull
  public final static String KIND_FILE = "file";
  @NotNull
  public final static String KIND_NONE = "none";
  @NotNull
  public final static String KIND_DIR = "dir";

  @NotNull
  public static final String URL_PREFIX = "svn://";

  public static final int ERROR_UNIMPLEMENTED = 210001;
  public static final int ERROR_NO_REVISION = 160006;
}