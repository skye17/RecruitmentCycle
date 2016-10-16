package akkastreams.RecruitmentCycle

/**
  * Created by skye17 on 16.10.16.
  */
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Keep, Sink, Source, ZipWith}

import scala.collection.immutable.{TreeMap, TreeSet}
import scala.concurrent.ExecutionContext.Implicits._


case class State(priority:Int, agent:URLAgent)

object RecruitmentCycleURL extends App {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  class AgentPriorityCycleFactory(firstAgent: URLAgent) {

    val cycle = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val agentsZipper = b.add  {
        ZipWith[URLAgent, List[URLAgent], List[URLAgent]] {
          case (agent:URLAgent, friendAgents:List[URLAgent]) =>
            val x = friendAgents
            //val x = (friendAgents:+agent).distinct
            x
        }

      }


      val priorityBufferScanner = b.add {
        Flow[List[URLAgent]].scan(
          (TreeMap(initialAgent.pk -> State(1, initialAgent)),
            TreeMap(1 -> TreeSet(initialAgent.pk)),
            State(1, initialAgent))) {
          case (
            (idBuffer:TreeMap[String, State], prBuffer:TreeMap[Int, TreeSet[String]], _),
            agentsList:List[URLAgent]) =>

            val (newIdBuf, newPrBuf) = agentsList.foldLeft((idBuffer, prBuffer)) {
              case ((idBuf:TreeMap[String, State], prBuf:TreeMap[Int, TreeSet[String]]), agent:URLAgent) =>
                idBuf.get(agent.pk) match {
                  case Some(State(pr, _)) =>
                    val newId = idBuf + (agent.pk -> State(pr + 1, agent))
                    val newPr = prBuf.get(pr) match {
                      case Some(ids) if ids.size == 1 =>
                        (prBuf - pr) + ((pr+1) -> (prBuf.getOrElse(pr+1, TreeSet.empty[String]) + agent.pk))
                      case Some(ids) =>
                        prBuf + (pr -> (ids - agent.pk)) + ((pr+1) -> (prBuf.getOrElse(pr+1, TreeSet.empty[String]) + agent.pk))

                        //must be impossible case
                      case None => prBuf
                    }

                    (newId, newPr)

                  case None =>
                    (idBuf + (agent.pk -> State(1, agent)),
                      prBuf + (1 -> (prBuf.getOrElse(1, TreeSet.empty[String]) + agent.pk)))

                }

            }

//            println("Id buf:")
//
//            newIdBuf foreach println
//
//            println("Pr buf:")
//
//            newPrBuf foreach println

            val bestPksSet = newPrBuf.last._2

            val bestAgentPk = bestPksSet.head

            val bestAgent = newIdBuf(bestAgentPk)

//            println("Best agent:")
//            println(bestAgent)
            //best

            val result: (TreeMap[String, State], TreeMap[Int, TreeSet[String]], State) =
              if (bestPksSet.size == 1) {
                (newIdBuf - bestAgentPk, newPrBuf - bestAgent.priority, bestAgent)
              }
              else {
                (newIdBuf - bestAgentPk, newPrBuf + (bestAgent.priority -> bestPksSet.tail), bestAgent)
              }

            result

        }
      }

      val mapper = b.add {
        Flow[(TreeMap[String, State], TreeMap[Int, TreeSet[String]], State)].map(_._3)
      }

      val agentsBroadcast = b.add {
        Broadcast[State](2)
      }

      val getFriends = b.add {
        Flow[State].map {
          state => state.agent.getFriends
        }
      }

      val resultBroadcast = b.add {
        Broadcast[TreeMap[String, State]](2)
      }

      val filterFriends = b.add{
        ZipWith[List[URLAgent], TreeMap[String, State], List[URLAgent]] {
          case (list, map) =>
            list.filterNot(agent => map.contains(agent.pk))
        }
      }

      val recruitsScanner = b.add {
        Flow[State].scan(TreeMap.empty[String, State]) {
          case (recruits, agentState) =>

            val newRecruits = recruits.get(agentState.agent.pk) match {
              case Some(_) => recruits
              case None => recruits + (agentState.agent.pk -> agentState)
            }

            //println(newRecruits.size)

            newRecruits
        }
      }



      val delayer = b.add {
        Flow[List[URLAgent]]
          .buffer(1, OverflowStrategy.backpressure)
      }

      val initialSource1 = b.add {
        Source.single(List.empty[URLAgent])
      }

      val initialSource2 = b.add {
        Source.single(TreeMap.empty[String, State])
      }

      val concat1 = b.add {
        Concat[List[URLAgent]]()
      }

      val concat2 = b.add {
        Concat[TreeMap[String, State]]()
      }

      initialSource1 ~> concat1

      initialSource2 ~> concat2

      agentsZipper.out ~> priorityBufferScanner ~> mapper ~> agentsBroadcast ~> recruitsScanner ~> resultBroadcast

      agentsBroadcast ~> getFriends ~> concat1 ~> filterFriends.in0

      resultBroadcast.out(0) ~> concat2 ~> filterFriends.in1

      filterFriends.out ~> delayer ~> agentsZipper.in1

      FlowShape(agentsZipper.in0, resultBroadcast.out(1))

    })
  }

  val initialPage = "https://en.wikipedia.org/wiki/Tinkoff"
  val initialAgent = new URLAgent(initialPage)

  val priorityCycle = new AgentPriorityCycleFactory(initialAgent).cycle

  val source = Source.fromIterator(() => Iterator.range(0, 100)
    .map {
      _ => initialAgent
    })


  val sink = Sink.last[TreeMap[String,State]]

  val res = source.via(priorityCycle).toMat(sink)(Keep.right).run()

  res.map {
    agentsMap => {
      println("Recruited")
      println(agentsMap.size)
      agentsMap.foreach {
        case (key, value) => println("Pr:"+ value.priority +"; url:"+ key)
      }
    }
  }

}

