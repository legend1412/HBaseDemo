package hbase

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{HTable, Put}
import org.apache.hadoop.hbase.mapred.TableOutputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapred.JobConf
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import utils.ConvertTime

object SparkHbase {
  def main(args: Array[String]): Unit = {
    try {
      /**
       * 在执行前，需要先创建hbase中的表
       * create 'badou.orders','id','num'
       *
       */
      //设置日志级别
      Logger.getLogger("org.apache.spark").setLevel(Level.ERROR)

      //    client 请求hbase，写数据 zookeeper
      val ZOOKEEPER_QUORUM = "192.168.137.3,192.168.137.4,192.168.137.5"
//      val ZOOKEEPER_QUORUM = "192.168.110.110:2183,192.168.110.110:2184,192.168.110.110:2185"
	  //表的权限不是root,hdfs上设置下面
      System.setProperty("HADOOP_USER_NAME", "root")
      val warehouse = "hdfs://master:9000/usr/soft/apache-hive-1.2.2-bin/warehouse"
      //    读取hive中的数据写入hbase，创建sparksession
      val spark = SparkSession.builder()
        .config("spark.sql.warehouse.dir", warehouse)
        .master("local[2]")
        .appName("spark to hbase")
        .enableHiveSupport()
        .getOrCreate()

      val rdd = spark.sql("select order_id,user_id,order_dow from badou.orders limit 300").rdd
      rdd.take(5).foreach(println)
      //[2539329,1,2]
      //[2398795,1,3]
      //[473747,1,3]
      //[2254736,1,4]
      //[431534,1,4]

      /**
       * 一个put对象就是一行记录，在构造方法中主键rowkey（user_id）
       * 所有插入的数据必须用org.apache.hadoop.hbase.util.Bytes
       **/
      rdd.map { row =>
        val order_id = row(0).asInstanceOf[String]
        val user_id = row(1).toString
        val order_dow = row(2).toString

        //加处理逻辑user_id为主key
        val p = new Put(Bytes.toBytes(user_id),ConvertTime.gettimestamp())
        //id 列族存放所有id类型列，order为列，value对应的order_id
        p.addColumn(Bytes.toBytes("id"), Bytes.toBytes("order"), Bytes.toBytes(order_id))
        //num为列族存放所有num数值型列，dow为列，order_dow为具体值
        p.addColumn(Bytes.toBytes("num"), Bytes.toBytes("dow"), Bytes.toBytes(order_dow))
        p
      }.foreachPartition { partiton =>
        //实例化配置信息
        val jobconf = new JobConf(HBaseConfiguration.create())
        jobconf.set("hbase.zookeeper.quorum", ZOOKEEPER_QUORUM)
        jobconf.set("hbase.zookeeper.property.clientPort", "2181")
        jobconf.set("zookeeper.znode.parent", "/hbase")
        jobconf.setOutputFormat(classOf[TableOutputFormat])
        //写入表名
        val table = new HTable(jobconf, TableName.valueOf("badou.orders"))
        //将scala中的seq结构转变成java的list结构
        import scala.collection.JavaConversions._
        table.put(seqAsJavaList(partiton.toSeq))
      }
    } catch {
      case ex: Exception =>
        println(ex.printStackTrace())
    }
  }
}
