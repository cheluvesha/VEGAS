package com.userlogUDF

import java.sql.SQLSyntaxErrorException
import com.Utility.{UtilityClass, WriteDataToSource}
import com.highestNoOfTimeLateComing.UserlogLateAnalysis
import com.idlehours.IdleHoursDriver
import org.apache.spark.sql.{DataFrame, SparkSession}

/***
  * UserLogUDF creates UDF for required computations such as percentage, hour minutes conversion
  */
object UserLogUDF {
  val sparkSession: SparkSession =
    UtilityClass.createSparkSessionObj("UserlogsUDF")
  var selectedColumnDF: DataFrame = _

  /***
    * sends hdfs file path to UserlogAnalysis class to find number of attendance
    * @param hdfsFilePath String
    * @param userlogLateAnalysis UserlogAnalysis
    * @return DataFrame
    */
  def findNoOfAttendance(
      hdfsFilePath: String,
      userlogLateAnalysis: UserlogLateAnalysis
  ): DataFrame = {
    val userlogsDF = userlogLateAnalysis.readFilesFromHDFS(hdfsFilePath)
    selectedColumnDF = userlogLateAnalysis.selectRequiredColumn(userlogsDF)
    val workingDays = userlogLateAnalysis.findNoOfWorkingDays(selectedColumnDF)
    val attendanceDF = userlogLateAnalysis.findUsersTotalNumberOfAttendance(
      workingDays,
      selectedColumnDF
    )
    attendanceDF
  }

  /***
    * UDF for hours and minute conversion
    */
  val hoursAndMinutes: Double => String = (decimalHour: Double) => {
    val hourPart = decimalHour.toInt
    val decimalPart: Double = decimalHour - hourPart
    val minutesPart = math.floor(decimalPart * 60).toInt
    val duration = hourPart + " hours " + minutesPart + " minutes"
    duration
  }

  /***
    * Applies UDF on IdleHoursDF
    * @param idleHoursDF DataFrame
    * @return DataFrame
    */
  def findIdleHoursForUsers(idleHoursDF: DataFrame): DataFrame = {
    try {
      sparkSession.udf.register("convertToHours", hoursAndMinutes)
      idleHoursDF.createOrReplaceTempView("IDLE_HOUR")
      val hoursFormattedDF = sparkSession.sql(
        """select username as UserName,convertToHours(idlehours) as IdleHours from IDLE_HOUR"""
      )
      hoursFormattedDF
    } catch {
      case sqlSyntaxErrorException: SQLSyntaxErrorException =>
        println(sqlSyntaxErrorException.printStackTrace())
        throw new Exception("Sql Syntax Error!!")
    }
  }

  /***
    * Performs query to count number of engineers in each tech
    * @return DataFrame
    */
  def countNoOfEngineersInEachTech(): DataFrame = {
    selectedColumnDF.createTempView("count_engineers")
    val countOfEngTechDF = sparkSession.sql(
      """select technology as Technology,count(distinct(user_name)) as UserName from count_engineers group by Technology"""
    )
    countOfEngTechDF
  }

  /***
    * UDF to find percentage
    */
  val findPercentage: (Int, Int) => Int = {
    (attendanceDays: Int, totalDays: Int) =>
      {
        val percentage = (attendanceDays * 100) / totalDays
        val roundedPercentage = math.floor(percentage).toInt
        roundedPercentage
      }
  }

  /***
    * Applies findPercentage UDF on attendanceDF
    * @param attendanceDF DataFrame
    * @return DataFrame
    */
  def findAttendancePercentage(attendanceDF: DataFrame): DataFrame = {
    try {
      sparkSession.udf.register("percentage", findPercentage)
      attendanceDF.createOrReplaceTempView("Attendance")
      val percentageDF = sparkSession.sql(
        """select user_name as UserName,percentage(attendance_count,noOfWorkingDays) as AttendancePercentage from Attendance"""
      )
      percentageDF
    } catch {
      case sqlSyntaxErrorException: SQLSyntaxErrorException =>
        println(sqlSyntaxErrorException.printStackTrace())
        throw new Exception("Sql Syntax Error!!")
    }
  }

  /***
    * Main method calls the respective methods
    * @param args Array[String]
    */
  def main(args: Array[String]): Unit = {
    val hdfsFilePath = args(0)
    val outPutPath = args(1)
    val userlogLateAnalysis = new UserlogLateAnalysis(sparkSession)
    val attendanceDF = findNoOfAttendance(hdfsFilePath, userlogLateAnalysis)
    val percentageDF = findAttendancePercentage(attendanceDF)
    val idleHoursDF = IdleHoursDriver.findIdleHours(hdfsFilePath)
    val hourFormattedDF = findIdleHoursForUsers(idleHoursDF)
    val countEngInTechDF = countNoOfEngineersInEachTech()
    val status1 = WriteDataToSource.writeDataFrameIntoJsonFormat(
      percentageDF,
      outPutPath + "/Attendance"
    )
    val status2 = WriteDataToSource.writeDataFrameIntoJsonFormat(
      hourFormattedDF,
      outPutPath + "/IdleHours"
    )
    val status3 = WriteDataToSource.writeDataFrameIntoJsonFormat(
      countEngInTechDF,
      outPutPath + "/Tech&Engg"
    )
    if (status1 && status2 && status3) {
      println(s"Files are written into $outPutPath Directory")
    } else {
      println("failed to write data into files")
    }
  }
}
