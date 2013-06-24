package controllers.util

import akka.actor.ActorSystem
import com.typesafe.config.Config
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}

class PrioritizedMailbox(settings: ActorSystem.Settings, config: Config) extends UnboundedPriorityMailbox(
  PriorityGenerator {
    case Message.GetSpatializationProgress | Message.SetSpatializationBatch => 0
    case Message.SpatializeTemperatureLog => 1
    case _ => 10
  })
