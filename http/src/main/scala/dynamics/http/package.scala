// Copyright (c) 2017 aappddeevv@gmail.com
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package dynamics

import scala.collection.mutable

import cats._
import cats.data._
import fs2._

package object http {

  type Service[A, B]      = Kleisli[Task, A, B]
  type Middleware         = Client => Client
  type HttpService        = Service[HttpRequest, HttpResponse]
  type StreamingClient[A] = fs2.Pipe[Task, HttpRequest, Task[A]]

  /** Non-streaming but good enough for our needs. */
  type Entity = Task[String]

  /** Basic headers are a dict of strings. */
  type HttpHeaders = collection.immutable.Map[String, Seq[String]]

  /**
    * When decoding a response body, either you get an A
    * or a DecodeFailure. The Task may also carry an exception.
    */
  type DecodeResult[A] = EitherT[Task, DecodeFailure, A]

}
