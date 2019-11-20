package com.janprach.sparktest.playground

import java.nio.file.Files

import com.janprach.sparktest.common.SparkTestBase
import com.janprach.sparktest.playground.SparkInput.InputTable

class SparkInputTest extends SparkTestBase {
  val dfs = new Dfs(sparkSession)

  test("listTables") {
    val dir = createLocalTemporaryDirectory()

    mkdirs(s"$dir/1/foo")
    mkdirs(s"$dir/10/foo")
    mkdirs(s"$dir/10/bar")
    mkdirs(s"$dir/11/foo", success = false)
    mkdirs(s"$dir/20/foo")

    val sparkInput = new SparkInput(sparkSession, SparkInputConfig(Seq.empty))
    assert(sparkInput.resolveInputTables(s"$dir/10?sos-listing_strategy=@") === Seq(
      InputTable("bar", s"file:$dir/10/bar", Map.empty, Map.empty),
      InputTable("foo", s"file:$dir/10/foo", Map.empty, Map.empty)))
  }

  test("parsePathParams") {
    import SparkInput.parsePathParams
    assert(parsePathParams("dir") === ("dir", Seq.empty))
    assert(parsePathParams("dir/file") === ("dir/file", Seq.empty))
    assert(parsePathParams("?param=value") === ("", Seq("param" -> "value")))
    assert(parsePathParams("dir/file?param1=value1&param2=&param3=value3&param3")
        === ("dir/file", Seq("param1" -> "value1", "param2" -> "", "param3" -> "value3", "param3" -> null)))
    assert(parsePathParams("dir?sos-listing_strategy=*<=42/@&foo=foo")
        === ("dir", Seq("sos-listing_strategy" -> "*<=42/@", "foo" -> "foo")))
  }

  def createLocalTemporaryDirectory(): String = {
    val directory = Files.createTempDirectory(this.getClass.getSimpleName + ".")
    directory.toFile.deleteOnExit()
    directory.toAbsolutePath.toString
  }

  def mkdirs(path: String, success: Boolean = true): Unit = {
    dfs.mkdirs(path)
    if (success) {
      dfs.createNewFile(s"$path/_SUCCESS")
    }
  }
}
