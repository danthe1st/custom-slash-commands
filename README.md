# Custom slash commands
![demo](https://github.com/danthe1st/custom-slash-commands/raw/assets/demo.gif)

## Setup
* Create an application on <https://discord.com/developers/applications/me> and add a bot to it.
* Create a file called `.token` and save your bot token in it.
* Clone this repository.
* If you want to use an IDE that supports maven, import the project in your IDE of choice as a maven project.
  
  Do not forget to install the [lombok](https://projectlombok.org/) plugin, however.
* Run the class `io.github.danthe1st.customslashcommands.Bot`.
  
  If you do not use an IDE that supports maven or lombok, install maven and run `mvn exec:java -Dexec.mainClass=io.github.danthe1st.customslashcommands.Bot`.
* Add the application using the link shown in the console (NOT using the bot-scope)
  
  The link should look like this: `https://discord.com/api/oauth2/authorize?client_id=YOUR_CLIENT_ID_HERE&scope=applications.commands`.
* Have fun
