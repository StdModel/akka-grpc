/**
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.scaladsl.play

import akka.grpc.gen.scaladsl.Service
import org.scalatest.{ Matchers, WordSpec }

class PlayScalaClientCodeGeneratorSpec extends WordSpec with Matchers {

  "The PlayScalaClientCodeGenerator" must {

    "choose the single package name" in {
      PlayScalaClientCodeGenerator
        .packageForSharedModuleFile(Seq(Service("a.b", "MyService", "???", Nil, false))) should ===("a.b")
    }

    "choose the longest common package name" in {
      PlayScalaClientCodeGenerator
        .packageForSharedModuleFile(Seq(
          Service("a.b.c", "MyService", "???", Nil, false),
          Service("a.b.e", "OtherService", "???", Nil, false))) should ===("a.b")
    }

    "choose the root package if no common packages" in {
      PlayScalaClientCodeGenerator
        .packageForSharedModuleFile(Seq(
          Service("a.b.c", "MyService", "???", Nil, false),
          Service("c.d.e", "OtherService", "???", Nil, false))) should ===("")
    }
  }

}
