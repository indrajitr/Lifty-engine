package org.lifty.engine

import org.lifty.engine.io._
import java.net.{ URL, URI }
import scalaz._
import scalaz.effects._
import Scalaz._
import org.fusesource.scalate.{ TemplateEngine }

trait Lifty extends InputParser {

  // A type that covers instance that can request input from the user
  type InputComponent = {
    def requestInput(msg: String, default: String): IO[String]
  }

  // The component to use when requesting input from the user
  val inputComponent: InputComponent
  
  // The storage component. Used to store/read recipes 
  val storageComponent: Storage

  // Given by SBT when being run as a plugin.
  val engine: Option[TemplateEngine]

  // This main method of interest. This takes a list of arguments and
  // executes the appropriate actions - if the input is valid it will
  // return a string describing what it created - otherwise an error
  // to display to the user.
  def run(args: List[String]): Validation[Error, String] = {        
    (for {
      parseCmdResult  <- parseCommand(args.toList).toOption
      (command, rest) = parseCmdResult
    } yield runCommand(command, rest)) getOrElse( Error("No such command").fail )
  }

  // Given a Command and a list and arguments this will run the actions
  // associated with the Command and return the result as a String.
  private def runCommand(command: Command, args: List[String]): Validation[Error, String] = {    
    command match {
      
      case RecipesCommand => {
        (
        "The following recipes are installed: " :: 
        "" ::
        storageComponent.allRecipes.unsafePerformIO.map(_.name).mkString("\n") :: Nil).mkString("\n").success
      }

      case LearnCommand => {        
        (for {
          name   <- args.headOption
          urlStr <- args.tail.headOption
        } yield {
          storageComponent.storeRecipe(name, new URL(urlStr)).unsafePerformIO.fold(
            error => error.fail,
            recipe => learnMsg(name,urlStr, recipe).success)
        }).getOrElse(Error("You have to supply a name and url for the recipe").fail)
      }
      
      case TemplatesCommand => {
        args.headOption.map { name => 
          descriptionOfRecipe(name).flatMap { description => 
            
            val max = description.templates.map(_.name.length).max
            
            (for {
              template <- description.templates
              output = "%s%s%s".format(
                template.name, 
                (0 to (max - template.name.length + 3)).map( _ => " ").mkString, // 3 for extra spacing
                template.description)
            } yield output).mkString("\n").success
          }
        } getOrElse( Error("You have to supply the name of the recipe").fail ) 
      }

      case HelpCommand =>
        (
        "help                         Shows this message" ::
        "create <recipe> <template>   Create a template from the given recipe" ::
        "templates <recipe>           List all the templates defined by the recipe" ::
        "learn <name> <url>           Learn the recipe at the given URL and store it locally under the given name" ::
        "delete <name>                Deletes a recipe. " ::
        "recipes                      Lists all installed recipes" ::
        "update <recipe>              Update the recipe if a new version exists" :: Nil).mkString("\n").success

      case CreateCommand => {
        args.headOption.map { name => 
          descriptionOfRecipe(name).flatMap { description => 
            parseTemplate(description, args.tail).flatMap { tuple => 
              val (template,rest) = tuple
              parseArguments(name, template, description).flatMap { env => 
                Scalate.run(env, description, engine).success
              }
            }
          }
        } getOrElse( Error("You have to supply the name of the recipe").fail )
      }
        
      case UpdateTemplatesCommand => {
        args.headOption.map { recipeName => 
          HomeStorage.recipe(recipeName).unsafePerformIO.fold(
            (e) => Error("No recipe with that name exists.").fail, 
            (s) => DescriptionLoader.load(s.descriptor).unsafePerformIO.fold(
              (e) => Error("Wasn't able to parse the local .json file. Please uninstall and re-learn the recipe.").fail,
              (s) => {
                val origin = new URL(s.origin)
                val version = s.version
                DescriptionLoader.load(origin).unsafePerformIO.fold(
                  (e) => e.fail,
                  (s) => if (s.version > version) {
                    HomeStorage.storeRecipe(recipeName, origin).unsafePerformIO.fold(
                      (e) => e.fail,
                      (s) => "Successfully updated the recipe.".format(version).success
                    )
                  } else {
                    "You have the most recent version of the recipe installed.".success
                  }
                )
              }
            )
          )   
        } getOrElse( Error("You have to supply the name of the recipe. ").fail )
      }
      
      case DeleteCommand => {
        args.headOption.map { recipeName => 
          HomeStorage.deleteRecipe(recipeName).unsafePerformIO.fold(
            (e) => e.fail,
            (s) => (s).success
          )
        } getOrElse( Error("You have to supply the name of the recipe. ").fail )
      }
      
      case _ =>
        Error("Command doesn't exist").fail
    }
  }
  
  private def descriptionOfRecipe(recipeName: String): Validation[Error,Description] = {
    storageComponent.recipe(recipeName).unsafePerformIO.flatMap { recipe => 
      DescriptionLoader.load(recipe.descriptor).unsafePerformIO
    } 
  }
  
  def learnMsg(name: String, url: String, recipe: Recipe) = {
    "Lifty successfully installed recipe with name '%s'. \n".format(name) +
    "\n"+
    "Run 'lifty templates %s' for information about the newly installed templates. \n".format(name) +
    "\n" +
    "Happy hacking." 
  }
}

// A specific configuration of Lifty which is used for tests.
object LiftyTestInstance extends Lifty {

  val inputComponent = EmulatedInputReaderComponent
  val storageComponent = HomeStorage
  val engine = None
}

// A specific configuration of Lifty which is used when running the 'real' 
// application.
class LiftyInstance(val engine: Option[TemplateEngine]) extends Lifty {

  val inputComponent = InputReaderComponent
  val storageComponent = HomeStorage

}

