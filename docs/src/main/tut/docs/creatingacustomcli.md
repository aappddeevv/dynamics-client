---
layout: docs
title: Create your own CLI
---

You can run the same actions as the default CLI under your control using:

```
import io.scalajs.nodejs._
import scala.concurrent.ExecutionContext.Implicits.global

// Fill in generic and action-specific args here.
// The most important is the AppConfig.connectInfo
val config = AppConfig(...)
val context = DynamicsContext.default(config)
val action = ... create standard/custom action to run...
val start = process.hrtime() // mark start of processing for default displayer below 

action(config). // your action may take more parameters than just config
  flatMap(_ => context.close()).
  unsafeRunAsync{attempt => 
     // display errors, if any, or use your own displayer
     dynamics.main.actionPostProcessor[Unit](config.noisy, start)(attempt)
     process.exit(0) // you need to do this since unsafeRunAsync runs in the background.
  }
  
...do more processing or if end of program, node will automatically wait until the action completes...
```
