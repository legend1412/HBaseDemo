package hbase

import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}
import org.apache.hadoop.hbase.client.{HTable, Put}
import org.apache.hadoop.hbase.mapred.TableOutputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapred.JobConf
import org.apache.spark.sql.SparkSession

object SparkHbase {
  def main(args: Array[String]): Unit = {
    //    client 请求hbase，写数据 zookeeper
    val ZOOKEEPER_QUORUM = "192.168.137.3,192.168.137.4,192.168.137.5"
    //    读取hive中的数据写入hbase，创建sparksession
    val spark = SparkSession.builder()
      .appName("spark to hbase")
      .enableHiveSupport()
      .getOrCreate()

    val rdd = spark.sql("select order_id,user_id,order_dow from badou.orders limit 300").rdd

    /**
     * 一个put对象就是一行记录，在构造方法中主键rowkey（user_id）
     * 所有插入的数据必须用org.apache.hadoop.hbase.util.Bytes
     * */
    rdd.map{row=>
      val order_id = row(0).asInstanceOf[String]
      val user_id = row(1).asInstanceOf[Long]
      val order_dow = row(2).asInstanceOf[Int]

      //      加处理逻辑user_id为主key
      var p = new Put(Bytes.toBytes(user_id))
      //      id 列族存放所有id类型列，order为列，value对应的order_id
      p.addColumn(Bytes.toBytes("id"),Bytes.toBytes("order"),Bytes.toBytes(order_id))
      //      num为列族存放所有num数值型列，dow为列，order_dow为具体值
      p.addColumn(Bytes.toBytes("num"),Bytes.toBytes("dow"),Bytes.toBytes(order_dow))
      p
    }.foreachPartition{partiton=>
      val jobconf = new JobConf(HBaseConfiguration.create())
      jobconf.set("hbase.zookeeper.quorum",ZOOKEEPER_QUORUM)
      jobconf.set("hbase.zookeeper.property.clientPort","2181")
      jobconf.set("zookeeper.znode.parent","/hbase")
      jobconf.setOutputFormat(classOf[TableOutputFormat])
      //      写入表名
      val table = new HTable(jobconf,TableName.valueOf("orders"))
      import scala.collection.JavaConversions._
      table.put(seqAsJavaList(partiton.toSeq))
    }
  }
}
