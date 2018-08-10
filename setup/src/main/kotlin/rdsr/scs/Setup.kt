package rdsr.scs

import java.io.File

val wDir = setupWorkingDir()
val buildDir = File(wDir, "build")
val logDir = setupLogDir()
val stdout = File(logDir, "scs.setup.out")
val stderr = File(logDir, "scs.setup.err")
val properties = config()

fun main(args: Array<String>) {
  if (properties["download.binaries"]?.toBoolean() == true)
    downloadBinaries()
  if (properties["setup.binaries"]?.toBoolean() == true)
    setupBinaries()
  if (properties["start.all"]?.toBoolean() == true)
    startAll()
  if (properties["stop.all"]?.toBoolean() == true)
    stopAll()
}

fun downloadBinaries() {
  // make sure we've stopped running processes before downloading binaries
  try { stopAll() } catch (e: Throwable) {}
  val dir = File(wDir, "binaries")
  exec("rm -rf $dir", wDir)
  exec("mkdir $dir", wDir)

  val hadoopTgzUrl = properties["hadoop.tgz.url"]
  exec("curl $hadoopTgzUrl -o hadoop.tar.gz", dir)

  val hiveTgzUrl = properties["hive.tgz.url"]
  exec("curl $hiveTgzUrl -o hive.tar.gz", dir)
}

fun setupBinaries() {
  // make sure we've stopped running processes before setting up binaries
  try { stopAll() } catch (e: Throwable) {}
  val dir = File(wDir, "binaries")

  exec("rm -rf hadoop", dir)
  exec("mkdir hadoop", dir)
  exec("tar zxvf hadoop.tar.gz -C hadoop --strip-components=1", dir)

  exec("rm -rf hive", dir)
  exec("mkdir hive", dir)
  exec("tar zxvf hive.tar.gz -C hive --strip-components=1", dir)
}

fun startAll() {
  startHadoop()
  startMetastore()
}

fun stopAll() {
  stopHadoop()
  stopMetastore()
}

fun startHadoop() {
  // make sure it is stopped, else there'd be process conflict on same ports
  stopHadoop()
  val dir = File(wDir, "binaries/hadoop")
  copyFiles(File(wDir, "conf/hadoop"), File(dir, "etc/hadoop"))

  val env = mapOf(
      "HADOOP_HOME" to "$dir",
      "HADOOP_CONF_DIR" to "$dir/etc/hadoop",
      "HDFS_HOME" to "$dir",
      "HDFS_CONF_DIR" to "$dir/etc/hadoop",
      "YARN_HOME" to "$dir",
      "YARN_CONF_DIR" to "$dir/etc/hadoop"
  )

  exec("bin/hdfs namenode -format -force", dir, env = env)
  exec("sbin/start-dfs.sh", dir, env = env)
  exec("sbin/start-yarn.sh", dir, env = env)
  writeEnvVars(env, dir)
}

fun stopHadoop() {
  val dir = File(wDir, "binaries/hadoop")
  val env = mapOf(
      "HADOOP_HOME" to "$dir",
      "HADOOP_CONF_DIR" to "$dir/etc/hadoop",
      "HDFS_HOME" to "$dir",
      "HDFS_CONF_DIR" to "$dir/etc/hadoop",
      "YARN_HOME" to "$dir",
      "YARN_CONF_DIR" to "$dir/etc/hadoop"
  )
  exec("sbin/stop-all.sh", dir, env = env)
}

fun startMetastore() {
  stopMetastore()
  val hadoopDir = File(wDir, "binaries/hadoop")
  val hiveDir = File(wDir, "binaries/hive")
  copyFiles(File(wDir, "conf/metastore"), File(hiveDir, "conf"))

  val env = mapOf(
      "HADOOP_HOME" to "$hadoopDir",
      "HADOOP_CONF_DIR" to "$hadoopDir/etc/hadoop",
      "HDFS_HOME" to "$hadoopDir",
      "HDFS_CONF_DIR" to "$hadoopDir/etc/hadoop",
      "HIVE_HOME" to "$hiveDir",
      "HIVE_HOME_CONF" to "$hiveDir/conf",
      "YARN_HOME" to "$hadoopDir",
      "YARN_CONF_DIR" to "$hadoopDir/etc/hadoop"
  )
  exec("${wDir}/bin/start-metastore.sh", hiveDir, env = env)
  writeEnvVars(env, hiveDir)
}

fun stopMetastore() {
  val hadoopDir = File(wDir, "binaries/hadoop")
  val hiveDir = File(wDir, "binaries/hive")
  val env = mapOf(
      "HADOOP_HOME" to "$hadoopDir",
      "HADOOP_CONF_DIR" to "$hadoopDir/etc/hadoop",
      "HDFS_HOME" to "$hadoopDir",
      "HDFS_CONF_DIR" to "$hadoopDir/etc/hadoop",
      "HIVE_HOME" to "$hiveDir",
      "HIVE_HOME_CONF" to "$hiveDir/conf",
      "YARN_HOME" to "$hadoopDir",
      "YARN_CONF_DIR" to "$hadoopDir/etc/hadoop"
  )
  exec("${wDir}/bin/stop-metastore.sh", hiveDir, env = env)
}

fun exec(
    cmdStr: String, wd: File,
    env: Map<String, String> = mapOf(), sys: Map<String, String> = mapOf()) {

  println("$cmdStr 1> ${stdout} 2> ${stderr} \n working dir: $wd \n env : $env \n sys: $sys \n")

  stdout.parentFile.mkdir()
  stderr.parentFile.mkdir()

  val sysProps = sys.toList().map { p -> "-D" + p.first + "=" + p.second }
  val cmd = cmdStr.split(" ")
  val pb = ProcessBuilder()
      .command(cmd + sysProps)
      .directory(wd)
      .redirectOutput(ProcessBuilder.Redirect.appendTo(stdout))
      .redirectError(ProcessBuilder.Redirect.appendTo(stderr))

  pb.environment().putAll(env)

  val process = pb.start()
  val rc = process.waitFor()
  if (rc != 0) {
    throw RuntimeException("Command $cmdStr failed return code: $rc")
  }
}

fun config(): Map<String, String> {
  val file = File(wDir, "conf/setup.cfg")
  val m = HashMap<String, String>()
  file.forEachLine { l ->
    if (l.isNotBlank()) {
      val kv = l.split("=")
      m[kv[0].trim()] = kv[1].trim()
    }
  }
  return m
}

fun writeEnvVars(env: Map<String, String>, dir: File) {
  val envVarsFile = File(dir, "env_vars.sh")
  envVarsFile.printWriter().use { out ->
    env.forEach { k, v -> out.println("export $k=$v") }
  }
}

fun setupWorkingDir(): File {
  val p = System.getProperty("project.dir")
  return if (p == null)
    File(System.getProperty("user.dir"), "setup")
  else
    File(p)
}

fun setupLogDir(): File {
  // clean up previous logs if any
  val f = File(buildDir, "logs")
  f.deleteRecursively()
  f.mkdirs()
  return f
}

fun copyFiles(src: File, dst: File) {
  src.listFiles().forEach { it.copyTo(File(dst, it.name), overwrite = true) }
}