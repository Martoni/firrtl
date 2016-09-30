// See LICENSE for license details.

package firrtlTests

import org.scalatest.{Matchers, FreeSpec}

import firrtl._
import scopt.OptionParser

class DriverSpec extends FreeSpec with Matchers {
  "CommonOptions are some simple options available across the chisel3 ecosystem" - {
    "CommonOption provide an scopt implementation of an OptionParser" - {
      "Options can be set from an Array[String] as is passed into a main" in {
        val commonOptions = new CommonOptions
        val parser = new OptionParser[Unit]("firrtl") {}

        commonOptions.addOptions(parser)

        parser.parse(Array.empty[String]) should be (true)

        commonOptions.topName should be ("")
        commonOptions.targetDirName should be ("test_run_dir")

        parser.parse(Array("--top-name", "dog", "--target-dir", "a/b/c")) should be (true)

        commonOptions.topName should be ("dog")
        commonOptions.targetDirName should be ("a/b/c")

        commonOptions.getBuildFileName(".fir") should be ("a/b/c/dog.fir")
        commonOptions.getBuildFileName("fir") should be ("a/b/c/dog.fir")
      }
      "CommonOptions can create a directory" in {
        var dir = new java.io.File("a/b/c")
        if(dir.exists()) {
          dir.delete()
        }
        val commonOptions = new CommonOptions
        val parser = new OptionParser[Unit]("firrtl") {}

        commonOptions.addOptions(parser)

        parser.parse(Array("--top-name", "dog", "--target-dir", "a/b/c")) should be (true)

        commonOptions.topName should be ("dog")
        commonOptions.targetDirName should be ("a/b/c")

        commonOptions.makeTargetDir() should be (true)
        dir = new java.io.File("a/b/c")
        dir.exists() should be (true)
        FileUtils.deleteDirectoryHierarchy(commonOptions.targetDirName)
      }
    }
  }
  "FirrtlOptions holds option information for the firrtl compiler" - {
    "It includes a CommonOptions" in {
      val firrtlOptions = new FirrtlExecutionOptions()
      firrtlOptions.targetDirName should be ("test_run_dir")
    }
    "It provides input and output file names based on target" in {
      val firrtlOptions = new FirrtlExecutionOptions()
      val parser = new OptionParser[Unit]("firrtl") {}

      firrtlOptions.addOptions(parser)

      parser.parse(Array("--top-name", "cat")) should be (true)

      firrtlOptions.inputFileName should be ("test_run_dir/cat.fir")
      firrtlOptions.outputFileName should be ("test_run_dir/cat.v")
    }
    "input and output file names can be overridden" in {
      val firrtlOptions = new FirrtlExecutionOptions()
      val parser = new OptionParser[Unit]("firrtl")
        {}

      firrtlOptions.addOptions(parser)

      parser.parse(
        Array("--top-name", "cat", "-fif", "./bob.fir", "-fof", "carol.v")
      ) should be (true)

      firrtlOptions.inputFileName should be ("./bob.fir")
      firrtlOptions.outputFileName should be ("test_run_dir/carol.v")
    }
  }
}
