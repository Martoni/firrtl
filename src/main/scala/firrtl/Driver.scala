// See License

package firrtl

import scopt.OptionParser

import scala.io.Source
import scala.collection.mutable
import Annotations._

import Parser.{InfoMode, IgnoreInfo, UseInfo, GenInfo, AppendInfo}
import scala.collection._

class CommonOptions {
  var topName:       String = ""
  var targetDirName: String = "test_run_dir"

  trait ParserOptions {
    self: OptionParser[Unit] =>

    note("common options")

    opt[String]("top-name").abbr("tn").valueName("<top-level-circuit-name>").foreach { x =>
      topName = x
    }.text("This options defines the top level circuit, defaults to dut when possible")
    opt[String]("target-dir").abbr("td").valueName("<target-directory>").foreach { x =>
      targetDirName = x
    }.text("This options defines a work directory for intermediate files")
  }

  /**
    * make sure that all levels of targetDirName exist
    *
    * @return true if directory exists
    */
  def makeTargetDir(): Boolean = {
    FileUtils.makeDirectory(targetDirName)
  }

  /**
    * return a file based on targetDir, topName and suffix
    *
    * @param suffix suffix to add, removes . if present
    * @return
    */
  def getBuildFileName(suffix: String): String = {
    val normalizedSuffix = if(suffix.startsWith(".")) suffix.drop(1) else suffix
    s"$targetDirName/$topName.$normalizedSuffix"
  }
}
/**
  * The options that firrtl supports in callable component sense
 *
  * @param inputFileNameOverride  default is targetDir/topName.fir
  * @param outputFileNameOverride default is targetDir/topName.v
  * @param compilerName           which compiler to use
  * @param infoModeName           use specific annotations
  * @param inferRW                use specific annotations
  * @param inLine                 in line modules
  */
class FirrtlExecutionOptions(
                              var inputFileNameOverride:  String = "",
                              var outputFileNameOverride: String = "",
                              var compilerName:           String = "verilog",
                              var infoModeName:           String = "use",
                              var inferRW:                Seq[String] = Seq(),
                              var inLine:                 Seq[String] = Seq(),
                              var commonOptions:          CommonOptions = new CommonOptions()
                            )
{
  trait ParserOptions {
    self: OptionParser[Unit] =>

    note("firrtl options")

    opt[String]("input-file").abbr("fif").valueName ("<firrtl-source>").foreach { x =>
      inputFileNameOverride = x
    } text {
      "use this to override the top name default"
    }

    opt[String]("output-file").abbr("fof").valueName ("<output>").foreach { x =>
      outputFileNameOverride = x
    } text {
      "use this to override the default name"
    }

    opt[String]("compiler").abbr("fc").valueName ("<high|low|verilog>").foreach { x =>
      compilerName = x
    }.validate { x =>
      if (Array("high", "low", "verilog").contains(x.toLowerCase)) success
      else failure(s"$x not a legal compiler")
    }.text {
      "compiler to use, default is verilog"
    }

    opt[String]("info-mode").valueName ("<ignore|use|gen|append>").foreach { x =>
      infoModeName = x.toLowerCase
    }.validate { x =>
      if (Array("ignore", "use", "gen", "append").contains(x.toLowerCase)) success
      else failure(s"$x not a legal compiler")
    }.text {
      "specifies the source info handling"
    }

    opt[Seq[String]]("infer-rw").abbr ("irw").valueName ("<ignore|use|gen|append>").foreach { x =>
      inferRW = x
    }.text {
      "specifies the source info handling"
    }

    opt[Seq[String]]("in-line").valueName ("<circuit>[.<module>][.<instance>]").foreach { x =>
      inLine = x
    }.text {
      "specifies what to in-line"
    }
    note("")
  }

  def infoMode: InfoMode = {
    infoModeName match {
      case "use" => UseInfo
      case "ignore" => IgnoreInfo
      case "gen" => GenInfo(inputFileNameOverride)
      case "append" => AppendInfo(inputFileNameOverride)
      case other => UseInfo
    }
  }

  def compiler: Compiler = {
    compilerName match {
      case "high" => new HighFirrtlCompiler()
      case "low" => new LowFirrtlCompiler()
      case "verilog" => new VerilogCompiler()
    }
  }

  def inputFileName: String = {
    if(inputFileNameOverride.isEmpty) {
      commonOptions.getBuildFileName("fir")
    }
    else {
      if(inputFileNameOverride.startsWith("./") ||
        inputFileNameOverride.startsWith("/")) {
        inputFileNameOverride
      }
      else {
        s"${commonOptions.targetDirName}/$inputFileNameOverride"
      }
    }
  }
  def outputFileName: String = {
    def suffix: String = {
      if(compilerName == "verilog") {
        "v"
      }
      else {
        "fir"
      }
    }
    if(outputFileNameOverride.isEmpty) {
      commonOptions.getBuildFileName(suffix)
    }
    else {
      if(outputFileNameOverride.startsWith("./") ||
        outputFileNameOverride.startsWith("/")) {
        outputFileNameOverride
      }
      else {
        s"${commonOptions.targetDirName}/$outputFileNameOverride"
      }
    }
  }
}

case class FirrtlExecutionResult(
                                emitted: String,
                                success: Boolean
                                )

object Driver {
  // Compiles circuit. First parses a circuit from an input file,
  //  executes all compiler passes, and writes result to an output
  //  file.
  def compile(
               input: String,
               output: String,
               compiler: Compiler,
               infoMode: InfoMode = IgnoreInfo,
               annotations: AnnotationMap = new AnnotationMap(Seq.empty)) = {
    val parsedInput = Parser.parse(Source.fromFile(input).getLines, infoMode)
    val outputBuffer = new java.io.CharArrayWriter
    compiler.compile(parsedInput, annotations, outputBuffer)

    val outputFile = new java.io.PrintWriter(output)
    outputFile.write(outputBuffer.toString)
    outputFile.close()
  }

  def execute(firrtlConfig: FirrtlExecutionOptions): Unit = {
    println(s"in Firrtl's Driver.execute with $firrtlConfig  ")
    compile(
      input = firrtlConfig.inputFileName,
      output = firrtlConfig.outputFileName,
      compiler = firrtlConfig.compiler,
      infoMode = firrtlConfig.infoMode
    )
  }

  def execute(args: Array[String]): Unit = {
    val commonOptions = new CommonOptions()
    val firrtlOptions = new FirrtlExecutionOptions(commonOptions = commonOptions)

    val parser = new OptionParser[Unit]("firrtl") with firrtlOptions.ParserOptions with commonOptions.ParserOptions

    parser.parse(args) match {
      case true =>
        execute(firrtlOptions)
      case _ =>
    }
  }

  def main(args: Array[String]): Unit = {
    execute(args)
  }
}

object FileUtils {
  /**
    * recursive create directory and all parents
    *
    * @param directoryName
    * @return
    */
  def makeDirectory(directoryName: String): Boolean = {
    val dirFile = new java.io.File(directoryName)
    if(dirFile.exists()) {
      if(dirFile.isDirectory) {
        true
      }
      else {
        false
      }
    }
    else {
      dirFile.mkdirs()
    }
  }

  /**
    * recursively delete all directories in a relative path
    * DO NOT DELETE absolute paths
    *
    * @param directoryPathName
    */
  def deleteDirectoryHierarchy(directoryPathName: String): Unit = {
    if(directoryPathName.isEmpty || directoryPathName.startsWith("/")) {
      // don't delete absolute path
    }
    else {
      val directory = new java.io.File(directoryPathName)
      if(directory.isDirectory) {
        directory.delete()
        val directories = directoryPathName.split("/+").reverse.tail
        if (directories.nonEmpty) {
          deleteDirectoryHierarchy(directories.reverse.mkString("/"))
        }
      }
    }
  }
}

object OldDriver {
  /**
    * Implements the default Firrtl compilers and an inlining pass.
    *
    * Arguments specify the compiler, input file, output file, and
    * optionally the module/instances to inline.
    */
  def main(args: Array[String]) = {
    val usage =
      """
Usage: firrtl -i <input_file> -o <output_file> -X <compiler> [options]
     sbt "run-main firrtl.Driver -i <input_file> -o <output_file> -X <compiler> [options]"

Required Arguments:
-i <filename>         Specify the input *.fir file
-o <filename>         Specify the output file
-X <compiler>         Specify the target compiler
                      Currently supported: high low verilog

Optional Arguments:
  --info-mode <mode>             Specify Info Mode
                                 Supported modes: ignore, use, gen, append
  --inferRW <circuit>            Enable readwrite port inference for the target circuit
  --inline <module>|<instance>   Inline a module (e.g. "MyModule") or instance (e.g. "MyModule.myinstance")

  --replSeqMem -c:<circuit>:-i:<filename>:-o:<filename>
  *** Replace sequential memories with blackboxes + configuration file
  *** Input configuration file optional
  *** Note: sub-arguments to --replSeqMem should be delimited by : and not white space!

  [--help|-h]                    Print usage string
"""

    def handleInlineOption(value: String): Annotation =
      value.split('.') match {
        case Array(circuit) =>
          passes.InlineAnnotation(CircuitName(circuit), TransID(0))
        case Array(circuit, module) =>
          passes.InlineAnnotation(ModuleName(module, CircuitName(circuit)), TransID(0))
        case Array(circuit, module, inst) =>
          passes.InlineAnnotation(ComponentName(inst, ModuleName(module, CircuitName(circuit))), TransID(0))
        case _ => throw new Exception(s"Bad inline instance/module name: $value" + usage)
      }

    def handleInferRWOption(value: String) = 
      passes.InferReadWriteAnnotation(value, TransID(-1))

    def handleReplSeqMem(value: String) = 
      passes.ReplSeqMemAnnotation(value, TransID(-2))

    run(args: Array[String],
      Map( "high" -> new HighFirrtlCompiler(),
        "low" -> new LowFirrtlCompiler(),
        "verilog" -> new VerilogCompiler()),
      Map("--inline" -> handleInlineOption _,
          "--inferRW" -> handleInferRWOption _,
          "--replSeqMem" -> handleReplSeqMem _),
      usage
    )
  }


  // Compiles circuit. First parses a circuit from an input file,
  //  executes all compiler passes, and writes result to an output
  //  file.
  def compile(
               input: String,
               output: String,
               compiler: Compiler,
               infoMode: InfoMode = IgnoreInfo,
               annotations: AnnotationMap = new AnnotationMap(Seq.empty)) = {
    val parsedInput = Parser.parse(Source.fromFile(input).getLines, infoMode)
    val outputBuffer = new java.io.CharArrayWriter
    compiler.compile(parsedInput, annotations, outputBuffer)

    val outputFile = new java.io.PrintWriter(output)
    outputFile.write(outputBuffer.toString)
    outputFile.close()
  }

  /**
   * Runs a Firrtl compiler.
   *
   * @param args list of commandline arguments
   * @param compilers mapping a compiler name to a compiler
   * @param customOptions mapping a custom option name to a function that returns an annotation
   * @param usage describes the commandline API
   */
  def run(args: Array[String], compilers: Map[String,Compiler], customOptions: Map[String, String=>Annotation], usage: String) = {
    /**
     * Keys commandline values specified by user in OptionMap
     */
    sealed trait CompilerOption
    case object InputFileName extends CompilerOption
    case object OutputFileName extends CompilerOption
    case object CompilerName extends CompilerOption
    case object InfoModeOption extends CompilerOption
    /**
     * Maps compiler option to user-specified value
     */
    type OptionMap = Map[CompilerOption, String]

    /**
     * Populated by custom annotations returned from corresponding function
     * held in customOptions
     */
    val annotations = mutable.ArrayBuffer[Annotation]()
    def nextOption(map: OptionMap, list: List[String]): OptionMap = {
      list match {
        case Nil => map
        case "-i" :: value :: tail =>
          nextOption(map + (InputFileName -> value), tail)
        case "-o" :: value :: tail =>
          nextOption(map + (OutputFileName -> value), tail)
        case "-X" :: value :: tail =>
          nextOption(map + (CompilerName -> value), tail)
        case "--info-mode" :: value :: tail =>
          nextOption(map + (InfoModeOption -> value), tail)
        case flag :: value :: tail if customOptions.contains(flag) =>
          annotations += customOptions(flag)(value)
          nextOption(map, tail)
        case ("-h" | "--help") :: tail => println(usage); sys.exit(0)
        case option :: tail =>
          throw new Exception("Unknown option " + option + usage)
      }
    }

    val arglist = args.toList
    val options = nextOption(Map[CompilerOption, String](), arglist)

    // Get input circuit/output filenames
    val input = options.getOrElse(InputFileName, throw new Exception("No input file provided!" + usage))
    val output = options.getOrElse(OutputFileName, throw new Exception("No output file provided!" + usage))

    val infoMode = options.get(InfoModeOption) match {
      case (Some("use") | None) => UseInfo
      case Some("ignore") => IgnoreInfo
      case Some("gen") => GenInfo(input)
      case Some("append") => AppendInfo(input)
      case Some(other) => throw new Exception("Unknown info mode option: " + other + usage)
    }

    // Execute selected compiler - error if not recognized compiler
    options.get(CompilerName) match {
      case Some(name) =>
        compilers.get(name) match {
          case Some(compiler) => compile(input, output, compiler, infoMode, new AnnotationMap(annotations.toSeq))
          case None => throw new Exception("Unknown compiler option: " + name + usage)
        }
      case None => throw new Exception("No specified compiler option." + usage)
    }
  }
}
