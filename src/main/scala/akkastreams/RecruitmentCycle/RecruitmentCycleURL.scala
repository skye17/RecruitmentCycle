package akkastreams.RecruitmentCycle

/**
  * Created by skye17 on 30.09.16.
  */

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source, ZipWith}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future


object RecruitmentCycleURL extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  implicit val actorSystem = ActorSystem()


  class AgentPriorityCycleFactory(firstAgent: URLAgent, minPriority: Int) {

    val cycle = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._


      val zipAgentsWithBuffers = b.add {
        ZipWith[URLAgent,
          (List[URLAgent], (Map[String, Int], Map[String, URLAgent])),
          (List[URLAgent], (Map[String, Int], Map[String, URLAgent]))] {

          case (agent: URLAgent, (friendAgents: List[URLAgent], stores: (Map[String, Int], Map[String, URLAgent]))) => {
            if (friendAgents.exists(ag => ag.pk == agent.pk)) (friendAgents, stores)
            else (friendAgents :+ agent, stores)
          }
        }
      }

      val priorityMapper = b.add {
        Flow[(List[URLAgent], (Map[String, Int], Map[String, URLAgent]))]
          .map {

            case (agentsList: List[URLAgent],
                  (pBuffer: Map[String, Int], allBuffer: Map[String, URLAgent])) =>
              val pairsForPriorityBuffer = for (agent <- agentsList) yield {
                pBuffer.get(agent.pk) match {
                  case Some(priority) =>
                    agent.pk -> (priority + 1)
                  case None => agent.pk -> 1
                }
              }

              val pairsForAllBuffer = for {
                agent <- agentsList
                if allBuffer.get(agent.pk).isEmpty
              } yield {
                agent.pk -> agent
              }

              val some = (pBuffer -- pairsForPriorityBuffer.map(_._1)) ++ pairsForPriorityBuffer

              (some, allBuffer ++ pairsForAllBuffer)
          }
      }


      val getHighest = b.add {
        Flow[(Map[String, Int], Map[String, URLAgent])]
          .map {

            case (buffer: Map[String, Int], all: Map[String, URLAgent]) =>

              val best = buffer.maxBy(_._2)
              all.get(best._1) match {
                case Some(bestAgent: URLAgent) =>
                  ((best._2, bestAgent), (buffer - bestAgent.pk, all - bestAgent.pk))
                case None =>
                  ((0, firstAgent), (buffer, all))
              }

          }
      }


      val broadcast = b.add {
        Broadcast[((Int, URLAgent), (Map[String, Int], Map[String, URLAgent]))](2)
      }

      val chooseNextAgents = b.add {
        Flow[((Int, URLAgent), (Map[String, Int], Map[String, URLAgent]))]
          .map {

            case ((pr, agent), stores) =>
              (agent.getFriends, stores)
          }
      }

      val delayer = b.add {
        Flow[(List[URLAgent], (Map[String, Int], Map[String, URLAgent]))]
          .buffer(100, OverflowStrategy.backpressure)
      }

      val recruitedAgentsScanner = b.add {
        Flow[((Int, URLAgent), (Map[String, Int], Map[String, URLAgent]))]
          .map(_._1)
          .scan(List.empty[(Int, URLAgent)]) {
            case (l: List[(Int, URLAgent)], (pr: Int, agent: URLAgent)) =>
              if (l.exists(ag => ag._2.pk == agent.pk)) l
              else l :+ (pr, agent)
          }
      }

      val resultBroadcast = b.add {
        Broadcast[List[(Int, URLAgent)]](2)
      }

      val zipRecruitsAndAgentFriends = b.add {
        ZipWith[List[(Int, URLAgent)], (List[URLAgent],
          (Map[String, Int], Map[String, URLAgent])),
          (List[URLAgent], (Map[String, Int], Map[String, URLAgent]))] {

          case (recruitedList: List[(Int, URLAgent)],
            (friendsList: List[URLAgent], stores: (Map[String, Int], Map[String, URLAgent]))) =>
            val recruits = recruitedList.map(_._2)

            (friendsList.filterNot(x => recruits.exists(y => y.pk == x.pk)), stores)
        }
      }

      val filterRecruits = b.add {
        Flow[List[(Int, URLAgent)]]
          .map {
            list => list.filter {
                case (pr: Int, agent: URLAgent) => pr >= minPriority
              }
          }
      }

      val concat1 = b.add {
        Concat[(List[URLAgent], (Map[String, Int], Map[String, URLAgent]))]()
      }

      val concat2 = b.add {
        Concat[List[(Int, URLAgent)]]()
      }

      val concat3 = b.add {
        Concat[(List[URLAgent], (Map[String, Int], Map[String, URLAgent]))]()
      }

      val initialSource = b.add {
        Source.single((List.empty[URLAgent], (Map.empty[String, Int], Map.empty[String, URLAgent])))
      }

      val initialSource2 = b.add {
        Source.single(List.empty[(Int, URLAgent)])
      }

      val initialSource3 = b.add {
        Source.single((List.empty[URLAgent], (Map.empty[String, Int], Map.empty[String, URLAgent])))
      }


      initialSource ~> concat1

      initialSource2 ~> concat2

      initialSource3 ~> concat3

      broadcast ~> chooseNextAgents ~> concat3 ~> zipRecruitsAndAgentFriends.in1

      zipAgentsWithBuffers.out ~> priorityMapper ~> getHighest ~> broadcast

      broadcast ~> recruitedAgentsScanner ~> resultBroadcast.in

      resultBroadcast ~> filterRecruits.in

      resultBroadcast ~> concat2 ~> zipRecruitsAndAgentFriends.in0

      zipRecruitsAndAgentFriends.out ~> delayer ~> concat1 ~> zipAgentsWithBuffers.in1

      FlowShape(zipAgentsWithBuffers.in0, filterRecruits.out)

    })
  }

  val initialPage = "https://en.wikipedia.org/wiki/Tinkoff"
  val initialAgent = new URLAgent(initialPage)

  val priorityCycle = new AgentPriorityCycleFactory(initialAgent, 1).cycle

  val source = Source.fromIterator(() => Iterator.range(0, 20)
    .map {
      _ => initialAgent
    })


  val sink = Sink.last[List[(Int, URLAgent)]]

  val res = source.via(priorityCycle).toMat(sink)(Keep.right).run()

  res.map {
    agents => {
      println("Recruited")
      agents foreach println
    }
  }

}
