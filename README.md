Chaos Monkey is a tool to randomly turn off and on AWS EC2 instances. It is directly inspired by [Netflix's account](http://techblog.netflix.com/2010/12/5-lessons-weve-learned-using-aws.html) of such script used to make sure that their cloud infrastructure could handle failures. There wasn't a public version of it, so I made my own. It's a total hack made for [The Next Web Hackathon](http://hackathon.thenextweb.com/index.php/Main_Page), so you'll want to to poke around a bit before running it on anything. You'll probably at least need to change the AWS credentials and region.

# Extending the monkey

There are a lot of ways Chaos Monkey could be improved:

- Add more mischief for the monkey to do: connect to a server and kill processes, throttle network connections, etc. See the `MischiefAction` trait and the `EC2Shutdown` case class, the only class currently implementing it.
- Add more targets: database servers, load balancers, etc. See the `MonkeyTarget` trait and the `EC2Target` class.
- Specifying target machines. Right all the machines in the given region are targeted.
- Better scheduling than `Thread.sleep()` and `new Timer().schedule()`.
- Track the state of targets so that one is not, for example, turned off while it is still off from a previous attack.
- Better logging than the text to stdout (though I think it is still very funny =)

# Credits

By Peter Robinett ([@pr1001](http://twitter.com/)) of [Bubble Foundry](http://www.bubblefoundry.com).

Released under the MIT license. See the LICENSE file for more.
