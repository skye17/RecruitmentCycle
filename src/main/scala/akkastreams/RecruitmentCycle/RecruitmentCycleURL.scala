package akkastreams.RecruitmentCycle

/**
  * Created by skye17 on 16.10.16.
  */
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source, ZipWith}

import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext.Implicits._


case class State(priority:Int, agent:URLAgent)

object RecruitmentCycleURL extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  class AgentPriorityCycleFactory(firstAgent: URLAgent, minPriority: Int) {

    val cycle = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val agentsZipper = b.add  {
        ZipWith[URLAgent, List[URLAgent], List[URLAgent]] {
          case (agent:URLAgent, friendAgents:List[URLAgent]) =>
            val x = friendAgents
            //val x = (friendAgents:+agent).distinct
            //println("Friends:")
            //x.foreach(println)
            x
        }

      }


      val priorityBufferScanner = b.add {
        Flow[List[URLAgent]].scan(
          (TreeMap(initialAgent.pk -> State(1, initialAgent)), State(1, initialAgent))) {
          case (
            (idBuffer:TreeMap[String, State], _), agentsList:List[URLAgent]) =>

            val k = agentsList.foldLeft(idBuffer) {
              case (buffer, agent) =>
                buffer + (agent.pk -> (buffer.get(agent.pk) match {
                  case Some(State(pr,_)) => State(pr+1, agent)
                  case None => State(1, agent)
                }))
            }

            val best = k.maxBy {
              case (pk, state) => state.priority
            }._2

            //println("Best agent:")
            //println(best)
            //best

            //val newBuf = k - best.agent.pk
            //println("Pr buffer:")
            //newBuf.foreach(println)
            //println
            //println(k)
            (k - best.agent.pk, best)
        }
      }

      val mapper = b.add {
        Flow[(TreeMap[String, State], State)].map(_._2)
      }

      val agentsBroadcast = b.add {
        Broadcast[State](2)
      }

      val getFriends = b.add {
        Flow[State].map {
          state => state.agent.getFriends
        }
      }

      val recruitsScanner = b.add {
        Flow[State].scan(List.empty[State]) {
          case (list, agentState) =>
            val recruits =
              if (list.exists(state => state.agent.pk == agentState.agent.pk)) list
              else list :+ agentState
            //println("Recruits:")
            //recruits foreach println
            //println
            recruits
        }
      }

      val delayer = b.add {
        Flow[List[URLAgent]]
          .buffer(1, OverflowStrategy.backpressure)
      }


      agentsZipper.out ~> priorityBufferScanner ~> mapper ~> agentsBroadcast ~> recruitsScanner.in

      agentsBroadcast ~> getFriends ~> delayer ~> agentsZipper.in1


      FlowShape(agentsZipper.in0, recruitsScanner.out)

    })
  }

  val initialPage = "https://en.wikipedia.org/wiki/Tinkoff"
  val initialAgent = new URLAgent(initialPage)

  val priorityCycle = new AgentPriorityCycleFactory(initialAgent, 1).cycle

  val source = Source.fromIterator(() => Iterator.range(0, 20)
    .map {
      _ => initialAgent
    })


  val sink = Sink.last[List[State]]

  val res = source.via(priorityCycle).toMat(sink)(Keep.right).run()

  res.map {
    agents => {
      println("Recruited")
      agents foreach println
    }
  }

}

