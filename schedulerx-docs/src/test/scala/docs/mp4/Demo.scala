package docs.mp4

/**
 * $ivy.`com.lihaoyi::os-lib:0.6.3`
 */
object Demo extends App {
  for (path <- os.walk(os.pwd, _.ext != "rm", maxDepth = 1)) {
    os.proc("ffmpeg", "-i", path.last, path.baseName + ".mp4").call()
  }
}
