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
      pageLinks.distinct
    }.map {
      pageLinks => pageLinks.filter(_.length <= 40).map(link => new URLAgent(link))
    }.getOrElse(List.empty[URLAgent])
  }

  override def toString = pk
}
