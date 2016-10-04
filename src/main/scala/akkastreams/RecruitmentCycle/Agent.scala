package akkastreams.RecruitmentCycle

import java.net.URL

import scala.util.Try
import scala.xml.Node

/**
  * Created by a.ermolaeva on 28.09.2016.
  */

trait Agent[T] {
  val pk:T
  def getFriends:List[Agent[T]]
}


class URLAgent(urlString:String) extends Agent[String] {
  override val pk = urlString
  val url = new URL(pk)
  val baseURL = "https://en.wikipedia.org"

  override def getFriends: List[URLAgent] = {
    Try {
      val content = HTML.load(url)
      val pageLinks = (for {
        realPage <- content
        a <- realPage \\ "a"
        href = a.attribute("href")
        if href.isDefined
        link = href.get.toString
        if !link.startsWith("#")
      } yield {
        if (link.startsWith("/")) baseURL + link
        else link
      }).toList
      pageLinks
    }.map {
      pageLinks => pageLinks.filter(_.length <= 80).map(link => new URLAgent(link))
    }.getOrElse(List.empty[URLAgent])
  }

  override def toString = pk
}

/*object test1 extends App {
  //val initialPage = "https://en.wikipedia.org/wiki/Tinkoff"
  val initialPage = "https://en.wikipedia.org//www.mediawiki.org/"
  val initialAgent = new URLAgent(initialPage)
  initialAgent.getFriends foreach println
}*/