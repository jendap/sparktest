package com.janprach.sparktest.playground

import org.apache.hadoop.fs.{FSDataInputStream, FileStatus, FileSystem, Path}
import org.apache.spark.sql.SparkSession

class Dfs(sparkSession: SparkSession) {
  private val hadoopConfiguration = sparkSession.sparkContext.hadoopConfiguration

  def path(path: String): Path = {
    new Path(path)
  }

  def fs(path: Path): FileSystem = {
    path.getFileSystem(hadoopConfiguration)
  }

  def asQualifiedPath(path: String): (FileSystem, Path) = {
    val hdfsPath = new Path(path)
    val fs = hdfsPath.getFileSystem(hadoopConfiguration)
    val qualifiedPath = hdfsPath.makeQualified(fs.getUri, fs.getWorkingDirectory)
    (fs, qualifiedPath)
  }

  def createNewFile(path: String): Boolean = {
    val (fs, qualifiedPath) = asQualifiedPath(path)
    fs.createNewFile(qualifiedPath)
  }

  def listStatus(path: String): Array[FileStatus] = {
    val (fs, qualifiedPath) = asQualifiedPath(path)
    fs.listStatus(qualifiedPath)
  }

  def mkdirs(path: String): Boolean = {
    val (fs, qualifiedPath) = asQualifiedPath(path)
    fs.mkdirs(qualifiedPath)
  }

  def open(path: String): FSDataInputStream = {
    val (fs, qualifiedPath) = asQualifiedPath(path)
    fs.open(qualifiedPath)
  }
}
