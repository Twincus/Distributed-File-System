package common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.*;

/**
 * Distributed filesystem paths.
 * <p>
 * <p>
 * Objects of type <code>Path</code> are used by all filesystem interfaces.
 * Path objects are immutable.
 * <p>
 * <p>
 * The string representation of paths is a forward-slash-delimeted sequence of
 * path components. The root directory is represented as a single forward
 * slash.
 * <p>
 * <p>
 * The colon (<code>:</code>) and forward slash (<code>/</code>) characters are
 * not permitted within path components. The forward slash is the delimeter,
 * and the colon is reserved as a delimeter for application use.
 */
public class Path implements Iterable<String>, Comparable<Path>, Serializable {
  public List<String> getComponents() {
    return components;
  }

  List<String> components;
  List<String> componentsView; // unmodifiable

  public Path(List<String> components) {
    this.components = new ArrayList<>(components);
  }

  /**
   * Creates a new path which represents the root directory.
   */
  public Path() {
    components = new ArrayList<>();
  }

  /**
   * Creates a new path by appending the given component to an existing path.
   *
   * @param path      The existing path.
   * @param component The new component.
   * @throws IllegalArgumentException If <code>component</code> includes the
   *                                  separator, a colon, or
   *                                  <code>component</code> is the empty
   *                                  string.
   */
  public Path(Path path, String component) {

    if (component.length() == 0 || component.contains(":") || component.contains("/")) {
      throw new IllegalArgumentException("In Path(), Component contains separator, colon, or is empty.");
    }


    this.components = new ArrayList<String>(path.components);
    this.components.add(component);
  }

  /**
   * Creates a new path from a path string.
   * <p>
   * <p>
   * The string is a sequence of components delimited with forward slashes.
   * Empty components are dropped. The string must begin with a forward
   * slash.
   *
   * @param path The path string.
   * @throws IllegalArgumentException If the path string does not begin with
   *                                  a forward slash, or if the path
   *                                  contains a colon character.
   */
  public Path(String path) {

    if(path.length() == 0 ){
      throw new IllegalArgumentException("In Path(), Component is empty.");
    }

    if (path.charAt(0) != '/' || path.contains(":")) {
      throw new IllegalArgumentException("Path does not begin with a forward slash or it contains a colon character.");
    }
    String[] tokens = path.split("/");
    this.components = new ArrayList<String>();
    for (String component : tokens) {
      if (component.length() > 0) {
        components.add(component);
      }
    }
  }

  /**
   * Returns an iterator over the components of the path.
   * <p>
   * <p>
   * The iterator cannot be used to modify the path object - the
   * <code>remove</code> method is not supported.
   *
   * @return The iterator.
   */
  @Override
  public Iterator<String> iterator() {
    if (componentsView == null) {
      componentsView = Collections.unmodifiableList(components);
    }
    return componentsView.iterator(); // unmodifiable
  }

  /**
   * Lists the paths of all files in a directory tree on the local
   * filesystem.
   *
   * @param directory The root directory of the directory tree.
   * @return An array of relative paths, one for each file in the directory
   * tree.
   * @throws FileNotFoundException    If the root directory does not exist.
   * @throws IllegalArgumentException If <code>directory</code> exists but
   *                                  does not refer to a directory.
   */
  public static Path[] list(File directory) throws FileNotFoundException {
      int parentDeep = directory.getAbsolutePath().split("/").length;
      List<Path> res = new ArrayList<>();
      Queue<File> que = new LinkedList<>();
      que.offer(directory);
      while (!que.isEmpty()) {
          File parent = que.poll();
          String[] children = parent.list();
          for (String str : children) {
              File child = new File(parent, str);
              if (child.isFile()) {
                  String[] childPath = child.getAbsolutePath().split("/");
                  List<String> comp = new ArrayList<>();
                  for (int i = parentDeep; i < childPath.length; i++) {
                      comp.add(childPath[i]);
                  }
                  res.add(new Path(comp));
              }
              else
                que.offer(child);
          }
      }
      Path[] resPath = new Path[res.size()];
      for (int i = res.size() - 1; i >= 0; i--) {
        resPath[i] = res.get(i);
      }
    return resPath;
  }


  /**
   * Determines whether the path represents the root directory.
   *
   * @return <code>true</code> if the path does represent the root directory,
   * and <code>false</code> if it does not.
   */
  public boolean isRoot() {
    return (components.size() == 0);
  }

  /**
   * Returns the path to the parent of this path.
   *
   * @throws IllegalArgumentException If the path represents the root
   *                                  directory, and therefore has no parent.
   */
  public Path parent() {
    if (isRoot()) {
      throw new IllegalArgumentException("This is root.");
    }
    Path parent = new Path();
    parent.components = new ArrayList<String>(this.components);
    parent.components.remove(parent.components.size() - 1);
    return parent;
  }

  /**
   * Returns the last component in the path.
   *
   * @throws IllegalArgumentException If the path represents the root
   *                                  directory, and therefore has no last
   *                                  component.
   */
  public String last() {
    if (isRoot()) {
      throw new IllegalArgumentException("This is root.");
    }
    return components.get(components.size() - 1);
  }

  /**
   * Determines if the given path is a subpath of this path.
   * <p>
   * <p>
   * The other path is a subpath of this path if it is a prefix of this path.
   * Note that by this definition, each path is a subpath of itself.
   *
   * @param other The path to be tested.
   * @return <code>true</code> If and only if the other path is a subpath of
   * this path.
   */
  public boolean isSubpath(Path other) {
    if (other.isRoot()) {
      return true;
    }
    if (other.components.size() > components.size()) {
      return false;
    }
    for (int i = 0; i < other.components.size(); i++) {
      if (!other.components.get(i).equals(components.get(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Converts the path to <code>File</code> object.
   *
   * @param root The resulting <code>File</code> object is created relative
   *             to this directory.
   * @return The <code>File</code> object.
   */
  public File toFile(File root) {
    return new File(root.getPath() + getAbsolutePath()); // need to test
  }

  // return /a/b/c NOT /a/b/c/. Absolute path with respect to the distributed system
  public String getAbsolutePath() {
    StringBuilder sb = new StringBuilder();
    for (String component : components) {
      sb.append("/");
      sb.append(component);
    }
    return sb.toString();
  }


  /**
   * Compares this path to another.
   * <p>
   * <p>
   * An ordering upon <code>Path</code> objects is provided to prevent
   * deadlocks between applications that need to lock multiple filesystem
   * objects simultaneously. By convention, paths that need to be locked
   * simultaneously are locked in increasing order.
   * <p>
   * <p>
   * Because locking a path requires locking every component along the path,
   * the order is not arbitrary. For example, suppose the paths were ordered
   * first by length, so that <code>/etc</code> precedes
   * <code>/bin/cat</code>, which precedes <code>/etc/dfs/conf.txt</code>.
   * <p>
   * <p>
   * Now, suppose two users are running two applications, such as two
   * instances of <code>cp</code>. One needs to work with <code>/etc</code>
   * and <code>/bin/cat</code>, and the other with <code>/bin/cat</code> and
   * <code>/etc/dfs/conf.txt</code>.
   * <p>
   * <p>
   * Then, if both applications follow the convention and lock paths in
   * increasing order, the following situation can occur: the first
   * application locks <code>/etc</code>. The second application locks
   * <code>/bin/cat</code>. The first application tries to lock
   * <code>/bin/cat</code> also, but gets blocked because the second
   * application holds the lock. Now, the second application tries to lock
   * <code>/etc/dfs/conf.txt</code>, and also gets blocked, because it would
   * need to acquire the lock for <code>/etc</code> to do so. The two
   * applications are now deadlocked.
   *
   * @param other The other path.
   * @return Zero if the two paths are equal, a negative number if this path
   * precedes the other path, or a positive number if this path
   * follows the other path.
   */
  @Override
  public int compareTo(Path other) {
    if (this.components.size() == other.components.size()) {
      return this.getAbsolutePath().compareTo(other.getAbsolutePath());
    } else {
      return this.components.size() - other.components.size();
    }
  }

  /**
   * Compares two paths for equality.
   * <p>
   * <p>
   * Two paths are equal if they share all the same components.
   *
   * @param other The other path.
   * @return <code>true</code> if and only if the two paths are equal.
   */
  @Override
  public boolean equals(Object other) {
    return (((Path) other).components.equals(this.components));
  }

  /**
   * Converts the path to a string.
   * <p>
   * <p>
   * The string may later be used as an argument to the
   * <code>Path(String)</code> constructor.
   *
   * @return The string representation of the path.
   */
  @Override
  public String toString() {
    if (components.size() == 0) {
      return "/";
    }
    return getAbsolutePath();
  }

  public static List<String> getComponents(String path) {
    String[] tokens = path.split("/");
    List<String> components = new ArrayList<>();
    for (String token : tokens) {
      if (token.length() != 0) {
        components.add(token);
      }
    }
    return components;
  }

  // given a b c, return /a, /a/b, /a/b/c in this order
  public static List<Path> getIncrementalPaths(Path longPath) {
    if (longPath.getComponents().size() == 0) {
      return new ArrayList<>(0);
    }
    List<Path> incrementalPaths = new ArrayList<>();
    List<String> components = longPath.getComponents();
    for (int i = 1; i <= components.size(); i++) {
      incrementalPaths.add(new Path(components.subList(0, i)));
    }
    return incrementalPaths;
  }

  // given a b c, return  , /a, /a/b, /a/b/c in this order
  public static List<Path> getIncrementalPathsWithRoot(Path longPath) {
    List<Path> incrementalPaths = new ArrayList<>();
    incrementalPaths.add(new Path());
    List<String> components = longPath.getComponents();
    for (int i = 1; i <= components.size(); i++) {
      incrementalPaths.add(new Path(components.subList(0, i)));
    }
    return incrementalPaths;
  }


  @Override
  public int hashCode() {
    return components.hashCode();
  }
}
