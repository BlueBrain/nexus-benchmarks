package ch.epfl.bluebrain.nexus.bench.cli

enum TestName(val value: String):
  case Read extends TestName("read")
  case ReadSource extends TestName("read-source")
  case Create extends TestName("create")
  case CreateNoValidation extends TestName("create-no-validation")
