import java.text.SimpleDateFormat
import org.specs2.mutable._
import controllers.util.DateFormatHelper
import java.util.{TimeZone, Calendar, Date}
import play.api.test.Helpers._
import play.api.test._

class UtilSpec extends Specification {
  "Timestamp" should {
    "be parsable with timezone" in {
      running(FakeApplication()) {
        val defaultTz = TimeZone.getDefault
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+9"))
        val ds = "2013-07-10T100000.764163Z09";
        //val tsDate = DateFormatHelper.ulmKmlTimestampFormatter.parse("2013-07-10T095956.764163Z+09")
        val tokens = ds.split("[\\.Z]");
        val fractionalSecs = tokens(1).toInt / 1000
        val dateStr = "%s.%03d+%s00".format(tokens(0), fractionalSecs, tokens(2))
        val date = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss.SSSZ").parse(dateStr);
        println(date)
        TimeZone.setDefault(defaultTz)
        println(date)
        val cal = Calendar.getInstance()
        cal.setTimeInMillis(1381327413754L)
        println(cal.getTime)
        println("")
        date must not beNull
      }
    }
  }
}
