package com.bubblefoundry.chaosmonkey

import scala.actors.Actor
import scala.math._
import scala.collection.JavaConversions._

import java.util.{Date, Timer, TimerTask, UUID}

import org.scala_tools.time.Imports._

import com.amazonaws._
import com.amazonaws.auth._
import com.amazonaws.services.ec2._
import com.amazonaws.services.ec2.model._

import com.codahale.logula.Logging
import org.apache.log4j.Level

object RandomListImplicit {
  implicit def l2rl[T](l: List[T]) = new {
    def randomItem: T = l(round(random * (l.length - 1)).toInt)
  }
}

// the actual mischief actions
trait MischiefAction {
  def causeMischief(instance: Instance)
  def repairMischief(instance: Instance)
  def name: String
}
case class EC2Shutdown(ec2: AmazonEC2Client, theName: String = "EC2Shutdown") extends MischiefAction {
  def name = theName
  def causeMischief(instance: Instance) = {
    val currentInstances = ec2.describeInstances.getReservations.flatMap(_.getInstances).toList
    // if running
    if (currentInstances.exists(anInstance => anInstance.getInstanceId == instance.getInstanceId && anInstance.getState.getName == "running")) {
      val stopped = ec2.stopInstances(new StopInstancesRequest(instance.getInstanceId :: Nil))
      val states = stopped.getStoppingInstances.map(_.getCurrentState)
    } else {
      // else wait and try again
      new Timer().schedule(new TimerTask() {
        def run {
          val stopped = ec2.stopInstances(new StopInstancesRequest(instance.getInstanceId :: Nil))
          val states = stopped.getStoppingInstances.map(_.getCurrentState)
        }
      }, 30.seconds.millis)
    }
    true
  }
  def repairMischief(instance: Instance) = {
    val currentInstances = ec2.describeInstances.getReservations.flatMap(_.getInstances).toList
    // if stopped
    if (currentInstances.exists(anInstance => anInstance.getInstanceId == instance.getInstanceId && anInstance.getState.getName == "stopped")) {
      val started = ec2.startInstances(new StartInstancesRequest(instance.getInstanceId :: Nil))
      val states = started.getStartingInstances.map(_.getCurrentState)
    } else {
      // else wait and try again
      new Timer().schedule(new TimerTask() {
        def run {
          val started = ec2.startInstances(new StartInstancesRequest(instance.getInstanceId :: Nil))
          val states = started.getStartingInstances.map(_.getCurrentState)
        }
      }, 30.seconds.millis)
    }
    true
  }
}

// the mischief logging item
case class Mischief(id: UUID = UUID.randomUUID, targetType: MonkeyTarget, instances: List[Instance], action: MischiefAction, start: Date = new Date, end: Option[Date] = None) {
  def managed(delta: Int) = copy(end = Some(new Date(start.getTime + delta)))
}

// receives mischief, queues them up to be reverted at later times
class MauradersMap extends Actor {
 // http://stackoverflow.com/questions/1560402/easiest-way-to-do-idle-processing-in-a-scala-actor/1565060#1565060
 def act = loop {
    reactWithin(0) {
      case msg: Mischief => // process msg
/*
      case TIMEOUT =>
        react {
          case msg: Mischief => // process msg
          // case msg: LowPriorityMessage => // process msg
        }
*/
    }
  } 
}

trait MonkeyTarget {
  val service: AmazonWebServiceClient
  val name: String
  val mischiefs: List[MischiefAction]
  
  import RandomListImplicit._
  def randomMischief = mischiefs.randomItem
  
  // make the new logging item which will contain all the current mischief information
  def makeNewMischief: Mischief
  
  def doMischief {
    val currentMischief = makeNewMischief
    
    // send mischief to MauradersMap logger
    // map ! currentMischief
    val instancesStr = currentMischief.instances.map(_.getInstanceId).mkString(", ")
    println("Aggghhhh! You did " + currentMischief.action.name + " to " + instancesStr + "!")
    val results = currentMischief.instances.map(instance => (currentMischief.action.causeMischief(instance), instance))
    
    // send mischiefs to MauradersMap logger/queuer for cleanup eventually
    // cleanup 100 to 10100 seconds after the start time
    // map ! currentMischief.managed(round(random * 10000) + 100)
    new Timer().schedule(new TimerTask() {
      def run {
        val fixeds = currentMischief.instances.map(instance => (currentMischief.action.repairMischief(instance), instance))
        println("Oh, what a nice monkey! He fixed the " + currentMischief.action.name + " he did on " + instancesStr + ".")
      }
    }, round(random * 2.minute.millis + 30.seconds.millis))
    // }, round(random * 1.hour.millis + 60.seconds.millis)) // happen anytime later from 60 seconds to 61 minutes
  }
}

class EC2Target(credentials: AWSCredentials) extends MonkeyTarget {
  val service = new AmazonEC2Client(credentials)
  // bah
  service.setEndpoint("ec2.us-east-1.amazonaws.com")
  val name = "EC2"
  
  def instances = service.describeInstances.getReservations.flatMap(_.getInstances)
  import RandomListImplicit._
  def randomInstance: Option[Instance] = if (!instances.isEmpty) Some(instances.toList.randomItem) else None
  
  val mischiefs = EC2Shutdown(ec2 = service) :: Nil
  
  // just do random mischief on one random instance at a time
  def makeNewMischief = Mischief(targetType = this, instances = randomInstance.toList, action = randomMischief)
}


trait MonkeyTalk
case object GoApeshit extends MonkeyTalk
case object BreakShit extends MonkeyTalk
case object BackToYourCage extends MonkeyTalk

/*
  Usage:
  val monkey = new ChaosMonkey(key = "bad", secret = "monkey")
  // start the actor
  monkey.start
  // start the mischief... combine with standard actor start?
  monkey ! GoApeshit
  // ...later
  monkey ! BackToYourCage
*/
class ChaosMonkey(key: String, secret: String) extends Actor {
  // org.apache.log4j.BasicConfigurator.configure
  
  Logging.configure { log =>
    log.registerWithJMX = true
    log.level = Level.WARN
    log.console.enabled = true
    log.console.threshold = Level.WARN
  }
  
  val credentials = new BasicAWSCredentials(key, secret)
  val ec2target = new EC2Target(credentials)
  // add other EC2 services targets later
  val targets = ec2target :: Nil  
  
  import RandomListImplicit._
  def randomTarget = targets.randomItem
  
  // FIXME: this is not actor code yet
  def act = loop {
    react {
      case GoApeshit => {
        println("Oh no, the Chaos Monkey broke out of his cage! Run for your lives!")
        // sleep for anywhere from 100 to 1100 seconds
        // Thread.sleep(round(random * 1000.seconds.millis + 100.seconds.millis).toInt)
        Thread.sleep(round(random * 30.seconds.millis + 1.seconds.millis).toInt)
        this ! BreakShit
      }
      case BreakShit => {
        val target = randomTarget
        println("Nooo, Chaos Monkey, not " + target.name + "!")
        target.doMischief
        // sleep for anywhere from 100 to 1100 seconds
        // Thread.sleep(round(random * 1000.seconds.millis + 100.seconds.millis).toInt)
        Thread.sleep(round(random * 1.minute.millis + 1.seconds.millis).toInt)
        this ! BreakShit
      }
      case BackToYourCage => {
        println("Bad monkey! Back to your cage!")
        exit()
      }
    }
  }
}

object ReleaseTheMonkey {
  def main(args: Array[String]) {
    val monkey = new ChaosMonkey(key = "AKIAIP4GCLLFPTSCHHSA", "c9vD+EfWYF2EsuhxENcUvSjXvFWO7aGQX1NnpTE7")
    monkey.start
    monkey ! GoApeshit
    // else wait and try again
    new Timer().schedule(new TimerTask() {
      def run {
        monkey ! BackToYourCage
      }
    }, 5.minutes.millis)
  }
}