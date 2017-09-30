# play-aws-signer
Play module for signing outgoing AWS requests. **WORK IN PROGRESS. PRE-ALPHA.**

...that said, it _does_ work with a development "installation" of [cerebro](https://github.com/lmenezes/cerebro) if you build it yourself (with `sbt package`). Drop the jar into the `lib` directory, and add the following lines to `conf/application.conf`:

```scala
play.modules.disabled += "play.api.libs.ws.ahc.AhcWSModule"
play.modules.enabled += "com.xogroupinc.play.AWSSignerModule"
```

You can provide creds either explicitly in that same file, using either the keys `aws.access.key.id` and `aws.secret.access.key` or the key `aws.profile`; or it will otherwise follow the [default chain of the Java AWS SDK](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html).
