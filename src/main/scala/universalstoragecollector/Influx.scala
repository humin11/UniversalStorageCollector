package universalstoragecollector

import scala.util.Properties.propOrElse
import scala.util.Try
import java.util.concurrent.TimeUnit

import org.influxdb.{InfluxDB, InfluxDBFactory}
import org.influxdb.InfluxDB.ConsistencyLevel
import org.influxdb.dto.Point.Builder
import org.influxdb.dto.{BatchPoints, Point}

class Influx(name: String, config: Map[String, String],
               sysConfig: Map[String, Option[String]])
  extends Output(name, config, sysConfig) with Logger {

  val loggerFile: String = propOrElse("USC_HOME", "") + "log/collector-error.log"

  val address: Option[String] =
    if (config.contains("address"))
      Some(config("address"))
    else
      None

  val port: Option[Int] =
    if (config.contains("port"))
      Some(config("port").toInt)
    else
      None

  val dbname: Option[String] =
    if (config.contains("dbname"))
      Some(config("dbname"))
    else
      None

  var influxDB: InfluxDB = _

  def isValid: Boolean = config.contains("address") & config.contains("port") &
    config.contains("dbname")

  def start(): Unit = {
    influxDB = InfluxDBFactory.connect("http://" + address.get + ":" + port.get)
  }

  def parseDouble(s: String): Option[Double] = Try { s.toDouble }.toOption

  def out(msg: Map[Int, (String, String)], timestamp: Long, data: Map[String, String]): Unit = {

    val header: Map[String, String] = (msg.values map (v => v._1 -> v._2)).toMap

    val batchPoints: BatchPoints = BatchPoints
      .database(dbname.get)
      .retentionPolicy("autogen")
      .consistency(ConsistencyLevel.ALL)
      .build()

    val point: Builder = Point.measurement(header("measurement"))
      .time(timestamp.toLong, TimeUnit.SECONDS)
      .tag("name", sysConfig("name").get)
      .tag("class", sysConfig("class").get)

    if (sysConfig("type").isDefined) point.tag("type", sysConfig("type").get)

    (header.keySet - "measurement") foreach {k => point.tag(k, header(k))}

    data.foreach {p =>
      if (parseDouble(p._2).isDefined) point.addField(p._1, parseDouble(p._2).get)
      else point.addField(p._1, p._2)
    }

    batchPoints.point(point.build())
    influxDB.write(batchPoints)
  }

  def stop(): Unit = {
    influxDB.close()
  }
}
