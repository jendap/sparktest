package com.janprach.sparktest.playground

import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.util.Locale

import org.apache.commons.io.IOUtils
import org.apache.spark.sql.{DataFrame, Dataset, Encoder, SparkSession}

import scala.reflect.runtime.universe._

case class SparkInputConfig(inputPaths: Seq[String])

class SparkInput(sparkSession: SparkSession, config: SparkInputConfig) {
  import SparkInput._

  private val dfs = new Dfs(sparkSession)

  registerTables()

  private def registerTables(): Unit = {
    val inputTables = config.inputPaths.flatMap(resolveInputTables)
    inputTables.foreach(registerInputTable)
  }

  def df(tableName: String): DataFrame = {
    sparkSession.table(tableName)
  }

  def ds[T <: Product: TypeTag: Encoder]: Dataset[T] = {
    df(getCollectionName[T]).as[T]
  }

  // TODO: multithreaded
  private[playground] def resolveInputTables(inputPath: String): Seq[InputTable] = {
    val (path, params) = parsePathParams(inputPath)
    val (rawSosParams, sparkParams) = params.toMap.partition(_._1.toLowerCase(Locale.ROOT).startsWith(SOS_PREFIX))
    val sosParams = rawSosParams.map(kv => (kv._1.toLowerCase(Locale.ROOT).stripPrefix(SOS_PREFIX), kv._2))

    val lastPathSegment = getLastPathSegment(path)
    val tableName = sosParams.get(SOS_TABLE_NAME)
    val listingStrategy = sosParams.get(SOS_LISTING_STRATEGY).map(_.toLowerCase(Locale.ROOT))
    val nameAndListingMsg = s"$SOS_PREFIX$SOS_TABLE_NAME and $SOS_PREFIX$SOS_LISTING_STRATEGY"
    val inputTables = (lastPathSegment, tableName, listingStrategy) match {
      case (_, Some(_), Some(_)) =>
        throw new IllegalArgumentException(s"Having both $nameAndListingMsg at the same time are not supported ($path)")
      case (SOS_LIST_PATTERN(_), Some(_), _) | (SOS_LIST_PATTERN(_), _, Some(_)) =>
        throw new IllegalArgumentException(s"Parameters $nameAndListingMsg are not supported *.list files ($path).")
      case (SOS_LIST_PATTERN(_), None, None) =>
        val is = dfs.open(path) // TODO: IO.using
        val content = IOUtils.toString(is, StandardCharsets.UTF_8)
        // TODO: async, cycles, max depth
        content.split('\n').flatMap(resolveInputTables).toSeq
      case (_, None, Some(strategy)) =>
        listTables(strategy, path, sosParams.filterKeys(_ != SOS_LISTING_STRATEGY), sparkParams)
      case (lastPathSegment, None, None) =>
        Seq(InputTable(lastPathSegment, path, sosParams, sparkParams))
      case (_, Some(tableName), None) =>
        Seq(InputTable(tableName, path, sosParams, sparkParams))
    }
    inputTables.sortBy(_.tableName)
  }

  private[playground] def listTables(strategy: String, path: String, sosParams: Map[String, String],
      sparkParams: Map[String, String]): Seq[InputTable] = {
    //    strategy.split('/')
    val statuses = dfs.listStatus(path)
    strategy match {
      case "@" => statuses.map(s => InputTable(s.getPath.getName, s.getPath.toString, sosParams, sparkParams))
      // TODO: timestamp based strategies
    }
  }

  private def registerInputTable(inputTable: InputTable): DataFrame = {
    var reader = sparkSession.read
    inputTable.sosParams.get(SOS_FORMAT).foreach(format => reader = reader.format(format))
    reader = reader.options(inputTable.sparkParams)
    val table = reader.load(inputTable.path)
    table.createOrReplaceTempView(inputTable.tableName)
    table
  }
}

object SparkInput {
  val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  val SOS_PREFIX = "sos-"
  val SOS_TABLE_NAME = "table_name"
  val SOS_FORMAT = "format"
  val SOS_LISTING_STRATEGY = "listing_strategy"
  val SOS_LIST_PATTERN = "(?i)(.*)\\.list".r

  case class InputTable(
      tableName: String, path: String, sosParams: Map[String, String], sparkParams: Map[String, String])

  def getCollectionName(tpe: Type): String = {
    // TODO: snake case
    tpe.dealias.typeSymbol.name.toString
//    tpe match {
//      case TypeRef(_, sym, _) => sym.asType.name.toString
//      case t => t.typeSymbol.name.toString
//    }
  }

  def getCollectionName[T <: Product: TypeTag]: String = {
    getCollectionName(implicitly[TypeTag[T]].tpe)
  }

  def splitOnFirstChar(value: String, splitChar: Char): (String, String) = {
    value.indexOf(splitChar, 0) match {
      case -1 => (value, null)
      case index => (value.substring(0, index), value.substring(index + 1))
    }
  }

  def getLastPathSegment(path: String): String = {
    path.indexOf('/', 0) match {
      case -1 => path
      case index => path.substring(index + 1)
    }
  }

  def parseParams(paramsString: String): Seq[(String, String)] = {
    paramsString.split('&').map(paramString => splitOnFirstChar(paramString, '='))
  }

  def parsePathParams(pathParamsString: String): (String, Seq[(String, String)]) = {
    val (path, paramsString) = splitOnFirstChar(pathParamsString, '?')
    val params = Option(paramsString).map(parseParams).getOrElse(Seq.empty)
    (path, params)
  }
}
