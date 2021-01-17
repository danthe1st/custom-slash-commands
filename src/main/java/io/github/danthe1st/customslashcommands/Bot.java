package io.github.danthe1st.customslashcommands;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.interaction.GatewayInteractions;
import discord4j.discordjson.json.*;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.RestClient;
import discord4j.rest.interaction.Interaction;
import discord4j.rest.interaction.InteractionHandler;
import discord4j.rest.interaction.InteractionResponse;
import discord4j.rest.interaction.Interactions;
import discord4j.rest.util.ApplicationCommandOptionType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Bot {

    private final GatewayDiscordClient client;
    private final Interactions interactions = Interactions.create();
    private Map <Long, Map <String, CustomCommand>> customCommands = new HashMap <>();

    public static void main(String[] args) {
        try {
            GatewayDiscordClient client = DiscordClient.create(readToken())
                    .login()
                    .block();
            Bot bot = new Bot(client);
            bot.createCommands();
        } catch(IOException e) {
            log.error("An IO error occured. Is the token stored in a file called .token? Does this user have the necessary permission to edit files in this directory?");
        } catch(ClassNotFoundException e) {
            log.error("Trying to modify internal files is not a good idea.");
        }
    }

    private static String readToken() throws IOException {
        try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(".token"), StandardCharsets.UTF_8))) {
            return br.readLine();
        }
    }

    private void createCommands() throws IOException, ClassNotFoundException {
        //deleteAllCommands(client);
        load();
        ApplicationCommandRequest managerCommand = ApplicationCommandRequest.builder()
                .name("command")
                .description("manages custom commands")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("list")
                        .description("Lists custom commands")
                        .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                        .isDefault(true)
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("add")
                        .description("Adds a custom command")
                        .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                        .addOption(
                                ApplicationCommandOptionData.builder()
                                        .name("name")
                                        .description("command name")
                                        .type(ApplicationCommandOptionType.STRING.getValue())
                                        .required(true)
                                        .build()
                        )
                        .addOption(
                                ApplicationCommandOptionData.builder()
                                        .name("description")
                                        .description("command description")
                                        .type(ApplicationCommandOptionType.STRING.getValue())
                                        .required(true)
                                        .build()
                        )
                        .addOption(
                                ApplicationCommandOptionData.builder()
                                        .name("Response")
                                        .description("command response")
                                        .type(ApplicationCommandOptionType.STRING.getValue())
                                        .required(true)
                                        .build()
                        )
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("remove")
                        .description("Removes a custom command")
                        .type(ApplicationCommandOptionType.SUB_COMMAND.getValue())
                        .addOption(
                                ApplicationCommandOptionData.builder()
                                        .name("name")
                                        .description("command name")
                                        .type(ApplicationCommandOptionType.STRING.getValue())
                                        .required(true)
                                        .build()
                        )
                        .required(false)
                        .build())
                .build();
        interactions
                .onGlobalCommand(managerCommand,
                        interaction -> interaction.acknowledge(true)
                                .withFollowup(resp -> handleManagerCommand(interaction, resp)));

        interactions.createCommands(client.getRestClient()).block();
        if(log.isInfoEnabled()){
            log.info("This application can be invited using the link https://discord.com/api/oauth2/authorize?client_id={}&scope=applications.commands",client.getSelfId().asString());
        }
        client.on(GatewayInteractions.create(interactions)).blockLast();
    }

    private Publisher <?> handleManagerCommand(Interaction interaction, InteractionResponse interactionResponse) {
        interactionResponse.deleteInitialResponse();
        String message="An error has occured.";
        ApplicationCommandInteractionData commandInfo = interaction.getData().data().get();
        ApplicationCommandInteractionOptionData option = commandInfo.options().get().get(0);
        String name = option.name();
        log.info("called {}", name);
        switch(name) {
            case "list":
                Map <String, CustomCommand> commands = customCommands.get(interaction.getGuildId().asLong());
                if(commands==null||commands.isEmpty()){
                    message="No commands exist.";
                }else{
                    message=commands.values().stream().map(String::valueOf).collect(Collectors.joining("\n"));
                }
                break;
            case "add":
                List <ApplicationCommandInteractionOptionData> addOptions = option.options().get();
                CustomCommand cmd = createCommand(interaction.getGuildId(), addOptions.get(0).value().get(), addOptions.get(1).value().get(), addOptions.get(2).value().get());//TODO second option?
                if(cmd==null){
                    message="Command cannot be added. Make sure the name has between 3 and 32 characters.";
                }else {
                    message = "Added command: "+cmd;
                }
                break;
            case "remove":
                List <ApplicationCommandInteractionOptionData> removeOptions = option.options().get();
                CustomCommand removed=removeCommand(interaction.getGuildId(),removeOptions.get(0).value().get());
                if(removed==null){
                    message="Command not found";
                }else{
                    message="Removed: "+removed;
                }
                break;
        }
        return interactionResponse.createFollowupMessage(message);
    }

    private CustomCommand removeCommand(Snowflake guildId, String name) {
        Map <String, CustomCommand> commandMap = customCommands.get(guildId.asLong());
        if(commandMap==null){
            return null;
        }
        CustomCommand removed=commandMap.remove(name);
        if(removed!=null){
            client.getRestClient().getApplicationService().deleteGuildApplicationCommand(client.getApplicationInfo().block().getId().asLong(), guildId.asLong(), removed.getId()).block();
            try {
                save();
            } catch(IOException e) {
                log.error("Cannot save",e);
            }
        }
        return removed;
    }

    private CustomCommand createCommand(Snowflake guild, String name, String description, String response) {
        if(name.length()<3||name.length()>=32){
            return null;
        }
        CustomCommand cmd = new CustomCommand(name, description, response);
        Map <String, CustomCommand> commandMap = customCommands.computeIfAbsent(guild.asLong(), k -> new HashMap <>());
        commandMap.put(name, cmd);
        RestClient restClient = client.getRestClient();
        ApplicationCommandRequest request = registerCommand(guild,cmd);
        ApplicationCommandData cmdData = restClient.getApplicationService().createGuildApplicationCommand(client.getApplicationInfo().block().getId().asLong(), guild.asLong(), request)
                .block();
        cmd.setId(Long.parseLong(cmdData.id()));
        interactions.createCommands(restClient);
        try {
            save();
        } catch(IOException e) {
            log.error("cannot save", e);
        }
        return cmd;
    }
    private ApplicationCommandRequest registerCommand(Snowflake guild,CustomCommand cmd){

        ApplicationCommandRequest request = ApplicationCommandRequest.builder()
                .name(cmd.getName())
                .description(cmd.getDescription())
                .build();
        interactions.onGuildCommand(
                request, guild, interaction -> interaction.acknowledge(true)
                        .withFollowup(resp -> {
                            resp.deleteInitialResponse();
                            return resp.createFollowupMessage(cmd.getResponse());
                        })
        );
        return request;
    }

    private void deleteAllCommands(GatewayDiscordClient client) {
        RestClient restClient = client.getRestClient();
        Long applicationId = restClient.getApplicationId().block();
        Flux <ApplicationCommandData> commands = restClient.getApplicationService().getGlobalApplicationCommands(applicationId);
        commands.toStream().forEach(command -> {
            restClient.getApplicationService().deleteGlobalApplicationCommand(applicationId, Long.parseLong(command.id())).block();
        });
        //try {
        //    save();
        //} catch(IOException e) {
        //    log.error("cannot save", e);
        //}
    }

    private void save() throws IOException {
        try(ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("commands.dat")))) {
            oos.writeObject(customCommands);
        }
    }

    private void load() throws IOException, ClassNotFoundException {
        File file=new File("commands.dat");
        if(file.exists()){
            try(ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                customCommands = (Map <Long, Map <String, CustomCommand>>) ois.readObject();
                customCommands.forEach((guild,commands)->{
                    commands.forEach((name,cmd)->{
                        registerCommand(Snowflake.of(guild),cmd);
                    });
                });
            }
        }
    }
}
