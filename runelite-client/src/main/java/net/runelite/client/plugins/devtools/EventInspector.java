package net.runelite.client.plugins.devtools;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.SliderUI;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * @author Kris | 22/10/2021
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
public class EventInspector extends DevToolsFrame {

    private final static int MAX_LOG_ENTRIES = 2500;
    private static final int VARBITS_ARCHIVE_ID = 14;
    private final Client client;
    private final EventBus eventBus;
    private final ProjectileTracker projectileTracker;
    private final JPanel tracker = new JPanel();
    private int lastTick = 0;
    private final Map<Skill, Integer> cachedExperienceMap = new HashMap<>();
    private final List<OverheadTextChanged> overheadChatList = new ArrayList<>();
    private final ClientThread clientThread;
    private int[] oldVarps = null;
    private int[] oldVarps2 = null;
    private boolean accessedObjectForAnimation = false;
    private Multimap<Integer, Integer> varbits;
    private final Set<Actor> facedActors = new HashSet<>();
    private final Map<Actor, Integer> facedDirectionActors = new HashMap<>();
    private final Map<Player, Integer> playerTransformations = new HashMap<>();
    private final Map<Long, Set<Integer>> updatedIfEvents = new HashMap<>();
    private final List<PendingSpawnUpdated> pendingSpawnList = new ArrayList<>();
    private final Map<WidgetNode, Pair<Long, Long>> ifMoveSubs = new HashMap<>();
    private final Map<Player, PlayerAppearance> appearances = new HashMap<>();
    private final Map<Integer, Integer> inventoryDiffs = new HashMap<>();
    private final Map<Player, Pair<PlayerMoved, WorldPoint>> movementEvents = new HashMap<>();
    private final Set<Player> movementTrackedPlayers = new HashSet<>();
    private WidgetNode lastMoveSub;
    private long hashTableNodeGet1 = -1;
    private long hashTableNodeGet2 = -1;
    private int latestInventoryId = -1;

    private int widgetScrollY = -1;
    private String widgetText = null;
    private int widgetSequence = -1;
    private int widgetColor = -1;

    private int widgetZoom = -1;
    private int widgetAngleX = -1;
    private int widgetAngleY = -1;

    private int widgetPositionX = -1;
    private int widgetPositionY = -1;

    private int widgetModelId = -1;

    private int widgetItemId = -1;
    private int widgetItemQuantityOrModelId = -1;
    private boolean widgetObjectType;

    private boolean widgetSetPlayerHead = false;

    private int widgetModelRotation = -1;

    private Boolean widgetHidden = null;

    private int widgetNpcId = -1;

    private int latestServerTick;
    /* A set for ignored scripts. There are some plugins which invoke procs through the client which we ignore. */
    private final Set<Integer> ignoredClientScripts = ImmutableSet.<Integer>builder().add(4029).build();

    private final JCheckBox projectiles = new JCheckBox("Projectiles", true);
    private final JCheckBox spotanims = new JCheckBox("Spotanims", true);
    private final JCheckBox sequences = new JCheckBox("Sequences", true);
    private final JCheckBox soundEffects = new JCheckBox("Sound Effects", true);
    private final JCheckBox areaSoundEffects = new JCheckBox("Area Sound Effects", true);
    private final JCheckBox say = new JCheckBox("Say", true);
    private final JCheckBox experience = new JCheckBox("Experience", true);
    private final JCheckBox messages = new JCheckBox("Messages", true);
    private final JCheckBox varbitsCheckBox = new JCheckBox("Varbits", false);
    private final JCheckBox varpsCheckBox = new JCheckBox("Varps", false);
    private final JCheckBox hitsCheckBox = new JCheckBox("Hits", false);
    private final JCheckBox interacting = new JCheckBox("Entity Facing", false);
    private final JCheckBox tileFacing = new JCheckBox("Tile Facing", false);
    private final JCheckBox clientScripts = new JCheckBox("Clientscripts", false);
    private final JCheckBox exactMove = new JCheckBox("Exact Move", true);
    private final JCheckBox combinedObjects = new JCheckBox("Combined Objects", true);
    private final JCheckBox transformations = new JCheckBox("Transformations", true);
    private final JCheckBox appearancesCheckbox = new JCheckBox("Appearances", true);
    private final JCheckBox ifEvents = new JCheckBox("Interface Events", true);
    private final JCheckBox inventoryChanges = new JCheckBox("Inventories", false);
    private final JCheckBox graphicsObjectChanges = new JCheckBox("Spotanim Specific", true);
    private final JCheckBox jingles = new JCheckBox("Jingles", true);
    private final JCheckBox hintArrows = new JCheckBox("Hint Arrows", true);
    private final JCheckBox camera = new JCheckBox("Camera", true);
    private final JCheckBox minimapState = new JCheckBox("Minimap State", true);
    private final JCheckBox mapObjectAdd = new JCheckBox("Map Object Add", false);
    private final JCheckBox mapObjectDel = new JCheckBox("Map Object Del", false);
    private final JCheckBox mapObjectAnim = new JCheckBox("Map Object Anim", true);
    private final JCheckBox groundItemAdd = new JCheckBox("Ground Item Add", false);
    private final JCheckBox groundItemDel = new JCheckBox("Ground Item Del", false);
    private final JCheckBox groundItemUpdate = new JCheckBox("Ground Item Update", false);
    private final JCheckBox ifOpenTop = new JCheckBox("Top Interfaces", false);
    private final JCheckBox ifOpenSub = new JCheckBox("Sub Interfaces", false);
    private final JCheckBox ifCloseSub = new JCheckBox("Close Sub Interfaces", false);
    private final JCheckBox ifMoveSub = new JCheckBox("Move Sub Interfaces", false);
    private final JCheckBox miscInterfacePackets = new JCheckBox("Misc. Interface Packets", true);
    private final JCheckBox playerMenuOptions = new JCheckBox("Player Menu Options", true);

    private final JCheckBox movement = new JCheckBox("Player Walk & Run", true);
    private final JCheckBox teleportation = new JCheckBox("Player Teleportation", true);

    private final List<JCheckBox> allSettings = new ArrayList<>();

    private final JCheckBox localPlayerOnly = new JCheckBox("Local Player Only", false);
    private final JCheckBox pauseButton = new JCheckBox("Pause", false);
    private final JCheckBox rsCoordFormat = new JCheckBox("RS Coordinate Format", false);
    private JSlider intervalSlider;

    private int maxEventDistance = 104;
    private int writeInterval = 100;

    private File outputFile;
    private final File settingsFile = new File(System.getProperty("user.home") + "/.openosrs/event-inspector-settings.txt");
    private final List<String> eventBuffer = new ArrayList<>(500);

    @Inject
    EventInspector(Client client, EventBus eventBus, ClientThread clientThread, ProjectileTracker projectileTracker) {
        this.client = client;
        this.eventBus = eventBus;
        this.clientThread = clientThread;
        this.projectileTracker = projectileTracker;
        setTitle("Event Inspector");
        setLayout(new BorderLayout());
        tracker.setLayout(new DynamicGridLayout(0, 1, 0, 3));
        final JPanel trackerWrapper = new JPanel();
        trackerWrapper.setLayout(new BorderLayout());
        trackerWrapper.add(tracker, BorderLayout.NORTH);

        final JScrollPane trackerScroller = new JScrollPane(trackerWrapper);
        trackerScroller.setPreferredSize(new Dimension(1400, 300));

        final JScrollBar vertical = trackerScroller.getVerticalScrollBar();
        vertical.addAdjustmentListener(new AdjustmentListener() {
            int lastMaximum = actualMax();

            private int actualMax() {
                return vertical.getMaximum() - vertical.getModel().getExtent();
            }

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                if (vertical.getValue() >= lastMaximum) {
                    vertical.setValue(actualMax());
                }
                lastMaximum = actualMax();
            }
        });

        add(trackerScroller, BorderLayout.CENTER);


        final JPanel trackerOpts = new JPanel(new DynamicGridLayout(0, 1, 0, 3));
        trackerOpts.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.BLACK));


        addZonePacketsPanel(trackerOpts);
        addMasksPanel(trackerOpts);
        addInterfacesPanel(trackerOpts);
        addMiscPanel(trackerOpts);

        trackerOpts.add(pauseButton);
        trackerOpts.add(localPlayerOnly);
        trackerOpts.add(rsCoordFormat);



        final JPanel sliderPanel = new JPanel(new BorderLayout());
        sliderPanel.setPreferredSize(new Dimension(250, 30));
        /* Manual spacing for string, cba messing around with layouts. */
        final JLabel distanceLabel = new JLabel("  Distance  ∞");
        sliderPanel.add(distanceLabel, BorderLayout.WEST);
        final JSlider slider = new JSlider(0, 15, 15);
        slider.setUI(new SliderUI(slider));
        slider.setPreferredSize(new Dimension(150, 30));
        sliderPanel.add(slider, BorderLayout.EAST);
        slider.addChangeListener(e -> {
            distanceLabel.setText("  Distance  " + (slider.getValue() == 15 ? "∞" : slider.getValue()));
            maxEventDistance = slider.getValue();
            if (maxEventDistance == 15) maxEventDistance = 104;
            writeSettingsFile();
        });

        trackerOpts.add(sliderPanel);

        final JPanel intervalSliderPanel = new JPanel(new BorderLayout());
        intervalSliderPanel.setPreferredSize(new Dimension(250, 30));
        /* Manual spacing for string, cba messing around with layouts. */
        final JLabel intervalLabel = new JLabel("  Interval  " + writeInterval);
        intervalSliderPanel.add(intervalLabel, BorderLayout.WEST);
        intervalSlider = new JSlider(0, 99, 99);
        intervalSlider.setUI(new SliderUI(intervalSlider));
        intervalSlider.setPreferredSize(new Dimension(150, 30));
        intervalSliderPanel.add(intervalSlider, BorderLayout.EAST);
        intervalSlider.addChangeListener(e -> {
            writeInterval = intervalSlider.getValue() + 1;
            intervalLabel.setText("  Interval  " + writeInterval);
            writeSettingsFile();
        });
        intervalSliderPanel.setToolTipText("<html>The interval slider defines how frequently, in server ticks(0.6 seconds each),<br>" +
                "the logs will be written to the file</html>.");

        trackerOpts.add(intervalSliderPanel);

        final JPanel enableButtonPanel = new JPanel(new GridLayout());

        final JButton enabledAllButton = new JButton("Enable all");
        enabledAllButton.addActionListener(e -> changeJCheckBoxStatus(trackerOpts, true));
        enableButtonPanel.add(enabledAllButton);

        final JButton disableAllButton = new JButton("Disable all");
        disableAllButton.addActionListener(e -> changeJCheckBoxStatus(trackerOpts, false));
        enableButtonPanel.add(disableAllButton);
        trackerOpts.add(enableButtonPanel);

        final JPanel splitAndClearPanel = new JPanel(new GridLayout());

        final JButton splitButton = new JButton("Split logs");
        splitButton.addActionListener(e -> {
            synchronized (eventBuffer) {
                if (outputFile == null) return;
                writeToFile();
                resetOutputFile();
            }
        });

        final JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            tracker.removeAll();
            tracker.revalidate();
        });
        splitAndClearPanel.add(splitButton);
        splitAndClearPanel.add(clearBtn);

        trackerOpts.add(splitAndClearPanel);

        add(trackerOpts, BorderLayout.EAST);

        for (Component component : trackerOpts.getComponents()) {
            if (!(component instanceof JCheckBox)) continue;
            if (component == pauseButton) continue;
            allSettings.add((JCheckBox) component);
            ((JCheckBox) component).addActionListener(e -> writeSettingsFile());
        }

        final JScrollPane scrollPane = new JScrollPane(trackerOpts);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.EAST);

        pack();
    }

    private void changeJCheckBoxStatus(JPanel parent, boolean value) {
        for (Component component : parent.getComponents()) {
            if (component == pauseButton || component == localPlayerOnly || component == rsCoordFormat) continue;
            if (component instanceof JCheckBox) {
                ((JCheckBox) component).setSelected(value);
            } else if (component instanceof JPanel) {
                changeJCheckBoxStatus((JPanel) component, value);
            }
        }
        writeSettingsFile();
    }

    private void addZonePacketsPanel(JPanel panel) {
        JLabel title = new JLabel("Zone Packets");
        title.setFont(new Font("Helvetica", Font.PLAIN, 14));
        panel.add(title);

        panel.add(projectiles);
        panel.add(areaSoundEffects);
        panel.add(combinedObjects);
        panel.add(graphicsObjectChanges);
        panel.add(mapObjectAdd);
        panel.add(mapObjectDel);
        panel.add(mapObjectAnim);
        panel.add(groundItemAdd);
        panel.add(groundItemDel);
        panel.add(groundItemUpdate);

        projectiles.setToolTipText("<html>The projectile inspector will require each unique projectile to be received from two" +
                "<br>different distances in order for it to be able to identify all of the projectile parameters." +
                "<br>This is due to one of the properties of projectile being the equivalent of" +
                "<br>lengthAdjustment + (chebyshevDistance * stepMultiplier).</html>");
        combinedObjects.setToolTipText("<html>Combined Objects refer to objects which have their models merged with the players' model" +
                " to fix model priority issues.<br>This is commonly used for agility shortcuts and obstacles, such as pipes.</html>");
    }

    private void addMasksPanel(JPanel panel) {
        JLabel title = new JLabel("Masks");
        title.setFont(new Font("Helvetica", Font.PLAIN, 14));
        panel.add(title);

        panel.add(appearancesCheckbox);
        panel.add(spotanims);
        panel.add(sequences);
        panel.add(say);
        panel.add(hitsCheckBox);
        panel.add(interacting);
        panel.add(tileFacing);
        panel.add(exactMove);
        panel.add(transformations);
        appearancesCheckbox.setToolTipText("<html>Appearances will only track changes done to a player's appearance.<br>" +
                "Therefore, on initial login/render of a character, everything about their appearance is logged, however,<br>" +
                "if they then equip an item for example, it'll only display differences in the equipment/body, as nothing else would be different.</html>");
        tileFacing.setToolTipText("<html>Tile facing will only display the direction that the character is facing, not the precise coordinate<br>" +
                "they were sent to face. This is because it is impossible to accurately determine which coordinate they're facing,<br>" +
                "as for example, facing south sends a direction of 0 - this could mean a coordinate 1 tile south of the character, or 10 tiles.</html>");
        say.setToolTipText("<html>Say will only display actual \"forced chat\" messages, not player-invoked public chat.</html>");
    }

    private void addInterfacesPanel(JPanel panel) {
        JLabel title = new JLabel("Interface Packets");
        title.setFont(new Font("Helvetica", Font.PLAIN, 14));
        panel.add(title);

        panel.add(messages);
        panel.add(clientScripts);
        panel.add(ifEvents);
        panel.add(ifOpenTop);
        panel.add(ifOpenSub);
        panel.add(ifCloseSub);
        panel.add(ifMoveSub);
        panel.add(miscInterfacePackets);
        miscInterfacePackets.setToolTipText("<html>Miscellaneous interface packets refer to the following packets:<br>" +
                "IfSetHide<br>" +
                "IfSetPosition<br>" +
                "IfSetScrollPos<br>" +
                "IfSetText<br>" +
                "IfSetAngle<br>" +
                "IfSetAnim<br>" +
                "IfSetColor<br>" +
                "IfSetModel<br>" +
                "IfSetObject<br>" +
                "IfModelRotate<br>" +
                "IfSetPlayerHead<br>" +
                "IfSetNpcHead</html>");
        messages.setToolTipText("<html>Messages may include plugin-created messages, and messages which plugins alter.</html>");
    }

    private void addMiscPanel(JPanel panel) {
        JLabel title = new JLabel("Misc Packets");
        title.setFont(new Font("Helvetica", Font.PLAIN, 14));
        panel.add(title);

        panel.add(inventoryChanges);
        panel.add(soundEffects);
        panel.add(jingles);
        panel.add(experience);
        panel.add(varpsCheckBox);
        panel.add(varbitsCheckBox);
        panel.add(hintArrows);
        panel.add(camera);
        panel.add(minimapState);
        panel.add(playerMenuOptions);
        panel.add(movement);
        panel.add(teleportation);
        panel.add(new JSeparator());
        camera.setToolTipText("<html>Camera packets include:<br>" +
                "CamReset<br>" +
                "CamShake<br>" +
                "CamLookAt<br>" +
                "CamMoveTo<br></html>");
        playerMenuOptions.setToolTipText("<html>Player menu option's index is 0-indexed, therefore the first option will have an index of 0.</html>");
        inventoryChanges.setToolTipText("<html>Inventories will only send differences compared to the cached version of the inventory<br>" +
                "due to how spammy the packet itself is in the events it transmits.</html>");
        movement.setToolTipText("<html>Movement will only record walking and running, and only within 15 tile radius around the local player.</html>");
        teleportation.setToolTipText("<html>Teleportation will only record teleports that happen within 15 tile radius of the local player.</html>");
    }

    private void addLine(String prefix, String text, boolean addToConsole, final JCheckBox checkBox) {
        addLine(prefix, text, client.getTickCount(), addToConsole, checkBox);
    }

    private void writeToFile() {
        if (outputFile == null) return;
        try {
            synchronized (eventBuffer) {
                if (eventBuffer.isEmpty()) return;
                BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, true));
                for (String line : eventBuffer) {
                    writer.write(line);
                    writer.newLine();
                }
                writer.flush();
                writer.close();
                eventBuffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getTickCount() % writeInterval == 0) {
            ForkJoinPool.commonPool().submit(this::writeToFile);
        }
    }

    private void addLine(String prefix, String text, int tick, boolean addToConsole, final JCheckBox checkBox) {
        final Date date = new Date();

        SwingUtilities.invokeLater(() -> {
            synchronized (eventBuffer) {
                eventBuffer.add(String.format(rsCoordFormat.isSelected() ? "%-125s" : "%-100s",
                        "[" + tick + "] " + new SimpleDateFormat("yyyy-MM-dd HH:mm:sss").format(date) + " " + prefix) + text);
                if (eventBuffer.size() >= 500) {
                    writeToFile();
                }
            }
            /* Log it externally here anyways */
            if (!addToConsole || !checkBox.isSelected() || pauseButton.isSelected()) return;
            if (tick != lastTick) {
                lastTick = tick;
                JLabel header = new JLabel("Tick " + tick);
                header.setFont(FontManager.getRunescapeSmallFont());
                header.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.LIGHT_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(3, 6, 0, 0)));
                tracker.add(header);
            }

            JPanel labelPanel = new JPanel();
            labelPanel.setLayout(new BorderLayout());
            JTextField prefixLabel = new JTextField(prefix);
            prefixLabel.setEditable(false);
            prefixLabel.setBackground(null);
            prefixLabel.setBorder(null);
            prefixLabel.setToolTipText(prefix);
            JTextField textLabel = new JTextField(text);
            textLabel.setEditable(false);
            textLabel.setBackground(null);
            textLabel.setBorder(null);
            prefixLabel.setPreferredSize(new Dimension(rsCoordFormat.isSelected() ? 600 : 400, 14));
            prefixLabel.setMaximumSize(new Dimension(rsCoordFormat.isSelected() ? 600 : 400, 14));
            labelPanel.add(prefixLabel, BorderLayout.WEST);
            labelPanel.add(textLabel);
            tracker.add(labelPanel);

            // Cull very old stuff
            while (tracker.getComponentCount() > MAX_LOG_ENTRIES) {
                tracker.remove(0);
            }

            tracker.revalidate();
        });
    }

    private int getDistance(LocalPoint localPoint) {
        return getDistance(WorldPoint.fromLocal(client, localPoint));
    }

    private int getDistance(WorldPoint worldPoint) {
        final Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) return 0;
        return localPlayer.getWorldLocation().distanceTo2D(worldPoint);
    }

    private boolean isActorConsoleLogged(Actor actor) {
        if (actor instanceof Player) {
            if (localPlayerOnly.isSelected()) {
                if (actor != client.getLocalPlayer()) return false;
            }
        }
        if (actor == null) return true;
        return getDistance(actor.getWorldLocation()) <= maxEventDistance;
    }

    private boolean inEventDistance(LocalPoint localPoint) {
        return getDistance(localPoint) <= maxEventDistance;
    }

    private boolean inEventDistance(WorldPoint worldPoint) {
        return getDistance(worldPoint) <= maxEventDistance;
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        projectileTracker.submitProjectileMoved(client, event, rsCoordFormat.isSelected(),
                (earlyProjectileInfo, dynamicProjectileInfo, prefix, text) -> {
                    final boolean inDistance =
                            Math.max(getDistance(dynamicProjectileInfo.getStartPoint()), getDistance(dynamicProjectileInfo.getEndPoint())) <= maxEventDistance;
                    final boolean console = inDistance && prefix.contains("Player(" + Objects.requireNonNull(client.getLocalPlayer()).getName());
                    addLine(prefix, text, console, projectiles);
                });
    }

    @Subscribe
    public void spotanimChanged(GraphicChanged event) {
        Actor actor = event.getActor();
        if (actor == null) return;
        String actorLabel = formatActor(actor);
        StringBuilder graphicsLabelBuilder = new StringBuilder();
        graphicsLabelBuilder.append("Graphics(");
        graphicsLabelBuilder.append("id = ").append(actor.getGraphic() == 65535 ? -1 : actor.getGraphic());
        final int delay = actor.getGraphicStartCycle() - client.getGameCycle();
        if (delay != 0) graphicsLabelBuilder.append(", delay = ").append(delay);
        if (actor.getGraphicHeight() != 0) graphicsLabelBuilder.append(", height = ").append(actor.getGraphicHeight());
        graphicsLabelBuilder.append(")");
        addLine(actorLabel, graphicsLabelBuilder.toString(), isActorConsoleLogged(actor), spotanims);
    }

    /**
     * Npc sequence changes need to be tracked through this method which is almost perfect, but there are
     * edge cases where if the animation matches the already-playing animation, the field hook may not get invoked.
     * For players, as the mask calls another function, we run this through another, always-executed function.
     */
    @Subscribe
    public void npcSequenceChanged(AnimationFrameIndexChanged event) {
        Actor actor = event.getActor();
        if (actor == null || actor instanceof Player || actor.getAnimationFrameIndex() != 0 || actor.getName() == null || isActorPositionUninitialized(actor)) return;
        postSequenceUpdate(actor, actor.getAnimation() == 65535 ? -1 : actor.getAnimation(), actor.getAnimationDelay());
    }

    @Subscribe
    public void playerSequenceChanged(PlayerAnimationPlayed event) {
        Actor actor = event.getPlayer();
        if (actor == null || actor.getName() == null || isActorPositionUninitialized(actor)) return;
        postSequenceUpdate(event.getPlayer(), event.getId(), event.getDelay());
    }

    private void postSequenceUpdate(Actor actor, int id, int delay) {
        String actorLabel = formatActor(actor);
        StringBuilder animationLabelBuilder = new StringBuilder();
        animationLabelBuilder.append("Animation(");
        animationLabelBuilder.append("id = ").append(id);
        if (delay != 0) animationLabelBuilder.append(", delay = ").append(delay);
        animationLabelBuilder.append(")");
        addLine(actorLabel, animationLabelBuilder.toString(), isActorConsoleLogged(actor), sequences);
    }

    private String formatLocation(WorldPoint point) {
        return formatLocation(point.getX(), point.getY(), point.getPlane(), false);
    }

    private String formatLocationOmitDecorations(WorldPoint point) {
        return formatLocation(point.getX(), point.getY(), point.getPlane(), true);
    }

    private String formatLocation(final int x, final int y, final int z, boolean omitDecorations) {
        if (rsCoordFormat.isSelected()) {
            final int msqx = x >> 6;
            final int msqz = y >> 6;
            final int tx = x & 0x3F;
            final int tz = y & 0x3F;
            final StringBuilder builder = new StringBuilder();
            if (!omitDecorations) builder.append("Location(");
            builder.append("level = ").append(z).append(", msqx = ").append(msqx)
                    .append(", msqz = ").append(msqz).append(", tx = ").append(tx).append(", tz = ").append(tz);
            if (!omitDecorations) builder.append(")");
            return builder.toString();
        } else {
            final StringBuilder builder = new StringBuilder();
            if (!omitDecorations) builder.append("Location(");
            builder.append("x = ").append(x).append(", y = ").append(y).append(", z = ").append(z);
            if (!omitDecorations) builder.append(")");
            return builder.toString();
        }
    }

    @Subscribe
    public void hintArrowChanged(HintArrowEvent event) {
        final int type = client.getHintArrowTargetType();
        switch (type) {
            /* Type 1 is for npc hint arrows */
            case 1:
                final NPC npc = client.getHintArrowNpc();
                addLine(npc == null ? "Out of boundaries hint arrow" : formatActor(npc),
                        "HintArrow(type = Npc, index = " + client.getHintArrowNpcTargetIdx() + ")", isActorConsoleLogged(npc), hintArrows);
                break;
            /* Type 1 is for player hint arrows */
            case 10:
                final Player player = client.getHintArrowPlayer();
                addLine(player == null ? "Out of boundaries hint arrow" : formatActor(player),
                        "HintArrow(type = Player, index = " + client.getHintArrowPlayerTargetIdx() + ")", isActorConsoleLogged(player), hintArrows);
                break;
            default:
                final LocalPoint localPoint = LocalPoint.fromScene(event.getHintArrowX(), event.getHintArrowY());
                final WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);
                addLine("Hint arrow" + type, "HintArrow(type = " + type
                        + ", " + formatLocation(worldPoint) + ")",
                        inEventDistance(localPoint), hintArrows);
                break;
        }
    }

    @Subscribe
    public void cameraResetEvent(CameraResetEvent event) {
        /* Since only the packet resets camera shaking, and because it is done in a loop, only accept the first one. */
        if (event.getId() != 0) return;
        addLine("Camera reset", "CamReset", true, camera);
    }

    @Subscribe
    public void cameraShakeEvent(CameraShakeEvent event) {
        addLine("Camera shake", "CamShake(type = " + event.getType()
                + ", shakeIntensity = " + event.getShakeIntensity()
                + ", movementIntensity = " + event.getMovementIntensity()
                + ", speed = " + event.getSpeed() + ")", true, camera);
    }

    @Subscribe
    public void cameraLookAtEvent(CameraLookAtEvent event) {
        final LocalPoint localPoint = LocalPoint.fromScene(event.getCameraLookAtX(), event.getCameraLookAtY());
        final WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);

        addLine("Camera look at", "CamLookAt(" + formatLocation(worldPoint) + ", "
        + "height = " + event.getCameraLookAtHeight() + ", speed = " + event.getCameraLookAtSpeed() + ", "
        + "acceleration = " + event.getCameraLookAtAcceleration() + ")", true, camera);
    }

    @Subscribe
    public void cameraMoveToEvent(CameraMoveToEvent event) {
        final LocalPoint localPoint = LocalPoint.fromScene(event.getCameraMoveToX(), event.getCameraMoveToY());
        final WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);
        addLine("Camera move to", "CamMoveTo(" + formatLocation(worldPoint) + ", "
                + "height = " + event.getCameraMoveToHeight() + ", speed = " + event.getCameraMoveToSpeed() + ", "
                + "acceleration = " + event.getCameraMoveToAcceleration() + ")", true, camera);
    }

    @Subscribe
    public void minimapStateEvent(MinimapStateChange event) {
        final MinimapState state = MinimapState.getState(event.getState());
        addLine("Minimap status change", "MinimapToggle(state = " + (state == null ? event.getState() : state) + ")", true, minimapState);
    }

    @Subscribe
    public void soundEffectPlayed(SoundEffectPlayed event) {
        final int soundId = event.getSoundId();
        final int delay = event.getDelay();
        final int loops = event.getLoops();
        StringBuilder soundEffectBuilder = new StringBuilder();
        soundEffectBuilder.append("SoundEffect(");
        soundEffectBuilder.append("id = ").append(soundId);
        if (delay != 0) soundEffectBuilder.append(", delay = ").append(delay);
        if (loops != 1) soundEffectBuilder.append(", repetitions = ").append(loops);
        soundEffectBuilder.append(")");
        addLine("Local", soundEffectBuilder.toString(), true, soundEffects);
    }

    @Subscribe
    public void areaSoundEffectPlayed(AreaSoundEffectPlayed event) {
        /* Animation-driven sounds will always have the source set to non-null, however that information is useless to us so skip it. */
        if (event.getSource() != null) return;
        final int soundId = event.getSoundId();
        final int delay = event.getDelay();
        final int loops = event.getLoops();
        final int radius = event.getRange();
        StringBuilder soundEffectBuilder = new StringBuilder();
        soundEffectBuilder.append("SoundEffect(");
        soundEffectBuilder.append("id = ").append(soundId);
        if (radius != 0) soundEffectBuilder.append(", radius = ").append(radius);
        if (delay != 0) soundEffectBuilder.append(", delay = ").append(delay);
        if (loops != 1) soundEffectBuilder.append(", repetitions = ").append(loops);
        soundEffectBuilder.append(")");
        WorldPoint location = WorldPoint.fromLocal(client, LocalPoint.fromScene(event.getSceneX(), event.getSceneY()));
        Optional<Player> sourcePlayer = client.getPlayers().stream().filter(player -> player.getWorldLocation().distanceTo(location) == 0).findAny();
        Optional<NPC> sourceNpc = client.getNpcs().stream().filter(npc -> npc.getWorldLocation().distanceTo(location) == 0).findAny();
        if (sourcePlayer.isPresent() && sourceNpc.isEmpty()) {
            addLine(formatActor(sourcePlayer.get()), soundEffectBuilder.toString(), isActorConsoleLogged(sourcePlayer.get()), areaSoundEffects);
        } else if (sourceNpc.isPresent() && sourcePlayer.isEmpty()) {
            addLine(formatActor(sourceNpc.get()), soundEffectBuilder.toString(), isActorConsoleLogged(sourceNpc.get()), areaSoundEffects);
        } else {
            addLine("Unknown(" + formatLocation(location) + ")", soundEffectBuilder.toString(), inEventDistance(location), areaSoundEffects);
        }
    }

    @Subscribe
    public void overheadTextChanged(OverheadTextChanged event) {
        Actor actor = event.getActor();
        if (actor == null) return;
        overheadChatList.add(event);
        latestServerTick = client.getTickCount();
    }

    /**
     * Due to the annoying nature of how overhead chat is handled by the client, the only way we can detect if a message was actually server-driven
     * or player-driven is to see if another field was changed shortly after. This strictly applies for player public chat, therefore it
     * works great as a means to detect overhead chat messages.
     */
    @Subscribe
    public void showPublicPlayerChatChanged(ShowPublicPlayerChatChanged event) {
        if (!overheadChatList.isEmpty()) {
            OverheadTextChanged element = overheadChatList.get(overheadChatList.size() - 1);
            overheadChatList.remove(element);
        }
    }

    @Subscribe
    public void experienceChanged(StatChanged event) {
        final int previousExperience = cachedExperienceMap.getOrDefault(event.getSkill(), -1);
        cachedExperienceMap.put(event.getSkill(), event.getXp());
        if (previousExperience == -1) return;
        final int experienceDiff = event.getXp() - previousExperience;
        if (experienceDiff == 0) return;
        addLine("Local", "Experience(skill = " + event.getSkill().getName() + ", xp = " + experienceDiff + ")", true, experience);
    }

    @Subscribe
    public void chatMessage(ChatMessage event) {
        ChatMessageType type = event.getType();
        String name = event.getName();
        if (name != null && !name.isEmpty()) {
            return;
        }
        addLine("Local", "Message(type = " + type + ", text = \"" + event.getMessage() + "\")", true, messages);
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Subscribe
    public void onClientTick(ClientTick event) {
        latestInventoryId = -1;
        /* Reset animation access as it may not have been reset manually due to a null check in the function. */
        accessedObjectForAnimation = false;
        if (!facedActors.isEmpty()) facedActors.clear();
        if (!facedDirectionActors.isEmpty()) {
            facedDirectionActors.forEach((faceDirectionActor, value) -> {
                if (faceDirectionActor == null || isActorPositionUninitialized(faceDirectionActor)) return;
                addLine(formatActor(faceDirectionActor), "FaceCoordinate(direction = " + value + ")", latestServerTick,
                        isActorConsoleLogged(faceDirectionActor), tileFacing);
            });
            facedDirectionActors.clear();
        }
        if (!overheadChatList.isEmpty()) {
            for (OverheadTextChanged message : overheadChatList) {
                String text = message.getOverheadText();
                addLine(formatActor(message.getActor()), "Say(text = \"" + text + "\")", latestServerTick, isActorConsoleLogged(message.getActor()), say);
            }
            overheadChatList.clear();
        }
        if (!updatedIfEvents.isEmpty()) {
            updatedIfEvents.forEach((packedKey, slots) -> {
                final int events = packedKey.intValue();

                final int interfaceId = (int) ((packedKey >> 48) & 0xFFFF);
                final int componentId = (int) ((packedKey >> 32) & 0xFFFF);

                for (Pair<Integer, Integer> range : getSlotRanges(new ArrayList<>(slots))) {
                    final StringBuilder eventBuilder = new StringBuilder();
                    eventBuilder.append("IfSetEvents(");
                    eventBuilder.append("interfaceId = ").append(interfaceId).append(", ");
                    eventBuilder.append("componentId = ").append(componentId).append(", ");
                    eventBuilder.append("startIndex = ").append(range.getLeft()).append(", ");
                    eventBuilder.append("endIndex = ").append(range.getRight()).append(", ");
                    eventBuilder.append("events = ").append(InterfaceEvent.sanitize(events)).append(")");
                    addLine("Interface event", eventBuilder.toString(), latestServerTick, true, ifEvents);
                }
            });
            updatedIfEvents.clear();
        }
        if (!pendingSpawnList.isEmpty()) {
            for (PendingSpawnUpdated update : pendingSpawnList) {
                final LocalPoint localPoint = LocalPoint.fromScene(update.getX(), update.getY());
                final WorldPoint location = WorldPoint.fromLocal(client, localPoint);
                /* Object id -1 implies an object removal. */
                if (update.getId() == -1) {
                    if (mapObjectDel.isSelected()) {
                        final StringBuilder locDelBuilder = new StringBuilder();
                        locDelBuilder.append("LocDel(");
                        locDelBuilder.append("slot = ").append(update.getType()).append(", ");
                        locDelBuilder.append("rotation = ").append(update.getOrientation()).append(", ");
                        locDelBuilder.append(formatLocation(location)).append(location.getPlane()).append(")");
                        addLine("Delete map object", locDelBuilder.toString(), latestServerTick, inEventDistance(localPoint), mapObjectDel);
                    }
                } else if (mapObjectAdd.isSelected()) {
                    final StringBuilder locAddBuilder = new StringBuilder();
                    locAddBuilder.append("LocAdd(");
                    locAddBuilder.append("id = ").append(update.getId()).append(", ");
                    locAddBuilder.append("slot = ").append(update.getType()).append(", ");
                    locAddBuilder.append("rotation = ").append(update.getOrientation()).append(", ");
                    locAddBuilder.append(formatLocation(location)).append(location.getPlane()).append(")");
                    addLine("Add map object", locAddBuilder.toString(), latestServerTick, inEventDistance(localPoint), mapObjectAdd);
                }
            }
            pendingSpawnList.clear();
        }
        if (!ifMoveSubs.isEmpty()) {
            if (ifMoveSub.isSelected()) {
                ifMoveSubs.forEach((node, value) -> {
                    final long fromTopInterfacePacked = value.getLeft();
                    final long packedInterface = value.getRight();
                    addLine("Move interface (id = " + node.getId() + ", walkType = " + node.getModalMode() + ")",
                            "IfMoveSub("
                                    + "fromTopInterface = " + (fromTopInterfacePacked >> 16) + ", " + "fromTopComponent = " + (fromTopInterfacePacked & 0xFFFF)
                                    + ", " + "toTopInterface = " + (packedInterface >> 16) + ", " + "toTopComponent = " + (packedInterface & 0xFFFF) + ")",
                            latestServerTick, true, ifMoveSub);
                });
            }
            ifMoveSubs.clear();
        }
        if (!movementEvents.isEmpty()) {
            movementTrackedPlayers.removeIf(player -> getDistance(player.getWorldLocation()) > 15);
            for (Pair<PlayerMoved, WorldPoint> pair : movementEvents.values()) {
                final PlayerMoved movementEvent = pair.getLeft();
                final WorldPoint previousLocation = pair.getRight();
                final LocalPoint localDestination = LocalPoint.fromScene(movementEvent.getX(), movementEvent.getY());
                final WorldPoint destination = WorldPoint.fromLocal(client, localDestination);
                final int distance = getDistance(localDestination);
                final boolean isLocalPlayer = movementEvent.getPlayer() == client.getLocalPlayer();
                if (!isLocalPlayer && (distance > 15 || movementEvent.getPlayer().getPlane() != client.getPlane())) continue;
                /* Any players that were just added to the tracked players list can't be relied on for valid info. */
                if (!isLocalPlayer && movementTrackedPlayers.add(movementEvent.getPlayer())) continue;
                if (movementEvent.getType() == 127) {
                    addLine(formatActor(movementEvent.getPlayer(), previousLocation),
                            "Teleport(" + formatLocation(destination) + ")",
                            isActorConsoleLogged(movementEvent.getPlayer()), teleportation);
                } else {
                    addLine(formatActor(movementEvent.getPlayer(), previousLocation),
                            "Movement(type = " + (movementEvent.getType() == 1 ? "Walk" : "Run") + ", " + formatLocation(destination) + ")",
                            isActorConsoleLogged(movementEvent.getPlayer()), movement);
                }
            }
            movementEvents.clear();
        }
    }

    @Subscribe
    public void onHashTableNodePut(HashTableNodePut event) {
        HashTable<? extends Node> table = event.getHashTable();
        if (table == client.getWidgetFlags()) {
            if (!(event.getNode() instanceof IntegerNode)) return;
            final int events = ((IntegerNode) event.getNode()).getValue();
            final long value = event.getValue();
            final int packedIf = (int) (value >> 32L);
            final int slot = (int) value;
            final long mapKey = (long) events | ((long) packedIf << 32);
            final Set<Integer> slots = updatedIfEvents.computeIfAbsent(mapKey, k -> new HashSet<>());
            latestServerTick = client.getTickCount();
            slots.add(slot);
        } else if (table == client.getComponentTable()) {
            if (!(event.getNode() instanceof WidgetNode)) return;
            final WidgetNode node = ((WidgetNode) event.getNode());
            final long value = event.getValue();
            ifMoveSubs.put(node, Pair.of(hashTableNodeGet1, value));
            lastMoveSub = node;
            latestServerTick = client.getTickCount();
            resetTrackedVariables();
        }
    }

    @Subscribe
    public void onHashTableNodeGet(HashTableNodeGetCall event) {
        hashTableNodeGet1 = hashTableNodeGet2;
        hashTableNodeGet2 = event.getKey();
    }

    @Subscribe
    public void onIfOpenTopReceived(IfOpenTopEvent event) {
        resetTrackedVariables();
        if (event.getRootInterface() == -1) return;
        addLine("Top interface", "IfOpenTop(id = " + event.getRootInterface() + ")", true, ifOpenTop);
    }

    @Subscribe
    public void onIfOpenSubReceiver(IfOpenSubEvent event) {
        resetTrackedVariables();
        /* Because open sub and move sub are quite similar in nature, we have to keep track of its state to filter out moves. */
        if (lastMoveSub != null) {
            ifMoveSubs.remove(lastMoveSub);
            lastMoveSub = null;
        }
        addLine("Sub interface", "IfOpenSub(id = " + event.getInterfaceId()
                + ", topInterface = " + (event.getTargetComponent() >> 16)
                + ", topComponent = " + (event.getTargetComponent() & 0xFFFF)
                + ", walkType = " + event.getWalkType() + ")", true, ifOpenSub);
    }

    @Subscribe
    public void onWidgetCloseReceived(WidgetClosed event) {
        resetTrackedVariables();
        if (!event.isUnload()) return;
        addLine("Close Sub Interface(id = " + event.getGroupId() + ", walkType = " + event.getModalMode() + ")",
                "IfCloseSub(topInterface = " + (hashTableNodeGet2 >> 16) + ", topComponent = " + (hashTableNodeGet2 & 0xFFFF) + ")", true, ifCloseSub);
    }

    private void resetTrackedVariables() {
        widgetScrollY = -1;
        widgetText = null;
        widgetSequence = -1;
        widgetColor = -1;
        widgetZoom = -1;
        widgetAngleX = -1;
        widgetAngleY = -1;
        widgetPositionX = -1;
        widgetPositionY = -1;
        widgetModelId = -1;
        widgetItemId = -1;
        widgetItemQuantityOrModelId = -1;
        widgetSetPlayerHead = false;
        widgetModelRotation = -1;
        widgetHidden = null;
        widgetNpcId = -1;
    }

    @Subscribe
    public void onWidgetConstructed(PostWidgetConstructed event) {
        resetTrackedVariables();
    }

    @Subscribe
    public void onServerPacketReadStartedEvent(ServerPacketReadStartedEvent event) {
        resetTrackedVariables();
    }

    @Subscribe
    public void onServerPacketReadCompleteEvent(ServerPacketReadCompleteEvent event) {
        if (widgetScrollY != -1) {
            addLine("Interface Scroll Position", "IfSetScrollPos(" + formatLatestWidgetCall() + ", scrollHeight = " + widgetScrollY + ")", true, miscInterfacePackets);
        } else if (widgetText != null) {
            addLine("Interface Text", "IfSetText(" + formatLatestWidgetCall() + ", text = \"" + widgetText + "\")", true, miscInterfacePackets);
        } else if (widgetSequence != -1) {
            addLine("Interface Sequence", "IfSetAnim(" + formatLatestWidgetCall() + ", animationId = " + widgetSequence + ")", true, miscInterfacePackets);
        } else if (widgetColor != -1) {
            final int red = (widgetColor >> 19) & 0x1F;
            final int green = (widgetColor >> 11) & 0x1F;
            final int blue = (widgetColor >> 3) & 0x1F;
            addLine("Interface Text Colour", "IfSetColor(" + formatLatestWidgetCall() + ", red = " + red + ", green = " + green + ", blue = " + blue + ")", true, miscInterfacePackets);
        } else if (widgetZoom != -1 && widgetAngleX != -1 && widgetAngleY != -1) {
            addLine("Interface Angle", "IfSetAngle(" + formatLatestWidgetCall()
                    + ", zoom = " + widgetZoom + ", angleX = " + widgetAngleX + ", angleY = " + widgetAngleY + ")", true, miscInterfacePackets);
        } else if (widgetPositionX != -1 && widgetPositionY != -1) {
            addLine("Interface Position", "IfSetPosition(" + formatLatestWidgetCall()
                    + ", x = " + widgetPositionX + ", y = " + widgetPositionY + ")", true, miscInterfacePackets);
        } else if (widgetModelId != -1) {
            addLine("Interface Model", "IfSetModel(" + formatLatestWidgetCall() + ", modelId = " + widgetModelId + ")", true, miscInterfacePackets);
        } else if (widgetItemId != -1 && widgetItemQuantityOrModelId != -1) {
            if (widgetObjectType) {
                addLine("Interface Object",
                        "IfSetObject(" + formatLatestWidgetCall() + ", itemId = " + widgetItemId + ", itemQuantity = " + widgetItemQuantityOrModelId + ")", true, miscInterfacePackets);
            } else {
                addLine("Interface Object",
                        "IfSetObject(" + formatLatestWidgetCall() + ", itemId = " + widgetItemId + ", modelZoom = " + widgetItemQuantityOrModelId + ")", true, miscInterfacePackets);
            }
        } else if (widgetSetPlayerHead) {
            addLine("Interface Player Head", "IfSetPlayerHead(" + formatLatestWidgetCall() + ")", true, miscInterfacePackets);
        } else if (widgetModelRotation != -1) {
            addLine("Interface Model Rotation",
                    "IfModelRotate(" + formatLatestWidgetCall() + ", pitch = " + (widgetModelRotation >> 16) + ", roll = " + (widgetModelRotation & 0xFFFF) + ")", true, miscInterfacePackets);
        } else if (widgetHidden != null) {
            addLine("Interface Visibility", "IfSetHide(" + formatLatestWidgetCall() + ", hidden = " + widgetHidden + ")", true, miscInterfacePackets);
        } else if (widgetNpcId != -1) {
            addLine("Interface Npc Head", "IfSetNpcHead(" + formatLatestWidgetCall() + ", npcId = " + widgetNpcId + ")", true, miscInterfacePackets);
        }
    }

    private String formatLatestWidgetCall() {
        final int latestWidgetCall = client.getLatestWidgetCall();
        return "interfaceId = " + (latestWidgetCall >> 16)
                + ", componentId = " + (latestWidgetCall & 0xFFFF);
    }

    @Subscribe
    public void onWidgetScrollHeightChanged(WidgetScrollHeightChanged event) {
        widgetScrollY = event.getScrollY();
    }

    @Subscribe
    public void onWidgetTextChanged(WidgetTextChanged event) {
        widgetText = event.getText();
    }

    @Subscribe
    public void onWidgetSequenceChanged(WidgetSequenceChanged event) {
        widgetSequence = event.getAnimationId();
    }

    @Subscribe
    public void onWidgetColorChanged(WidgetColorChanged event) {
        widgetColor = event.getColor();
    }

    @Subscribe
    public void onWidgetZoomChanged(WidgetZoomChanged event) {
        widgetZoom = event.getZoom();
        widgetAngleX = event.getAngleX();
        widgetAngleY = event.getAngleY();
    }

    @Subscribe
    public void onWidgetPositionChanged(WidgetPositionChanged event) {
        widgetPositionX = event.getX();
        widgetPositionY = event.getY();
    }

    @Subscribe
    public void onWidgetModelChanged(WidgetModelChanged event) {
        widgetModelId = event.getModelId();
    }

    @Subscribe
    public void onWidgetObjectChanged(WidgetSetObject event) {
        widgetItemId = event.getItemId();
        widgetItemQuantityOrModelId = event.getItemQuantityOrModelZoom();
        widgetObjectType = event.isNewType();
    }

    @Subscribe
    public void onWidgetSetPlayerHead(WidgetSetPlayerHead event) {
        widgetSetPlayerHead = true;
    }

    @Subscribe
    public void onWidgetModelRotation(WidgetModelRotate event) {
        widgetModelRotation = event.getRotation();
    }

    @Subscribe
    public void onWidgetVisibilityChange(WidgetVisibilityChange event) {
        widgetHidden = event.isHidden();
    }

    @Subscribe
    public void onWidgetNpcHeadChange(WidgetSetNpcHead event) {
        widgetNpcId = event.getNpcId();
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged) {
        int index = varbitChanged.getIndex();
        int[] varps = client.getVarps();
        boolean isVarbit = false;
        for (int i : varbits.get(index)) {
            int old = client.getVarbitValue(oldVarps, i);
            int newValue = client.getVarbitValue(varps, i);
            String name = null;
            for (Varbits varbit : Varbits.values()) {
                if (varbit.getId() == i) {
                    name = varbit.name();
                    break;
                }
            }
            if (old != newValue) {
                client.setVarbitValue(oldVarps2, i, newValue);
                String prefix = name == null ? "Varbit" : ("Varbit \"" + name + "\"");
                addLine(prefix + " (varpId: " + index + ", oldValue: " + old + ")", "Varbit(id = " + i + ", value = " + newValue + ")", true, varbitsCheckBox);
                isVarbit = true;
            }
        }
        if (isVarbit) return;

        int old = oldVarps2[index];
        int newValue = varps[index];
        if (old != newValue) {
            String name = null;
            for (VarPlayer varp : VarPlayer.values()) {
                if (varp.getId() == index) {
                    name = varp.name();
                    break;
                }
            }
            String prefix = name == null ? "Varp" : ("Varp \"" + name + "\"");
            addLine(prefix + " (oldValue: " + old + ")", "Varp(id = " + index + ", value = " + newValue + ")", true, varpsCheckBox);
        }

        System.arraycopy(client.getVarps(), 0, oldVarps, 0, oldVarps.length);
        System.arraycopy(client.getVarps(), 0, oldVarps2, 0, oldVarps2.length);
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        Actor actor = event.getActor();
        if (actor == null || isActorPositionUninitialized(actor)) return;
        Hitsplat hitsplat = event.getHitsplat();
        addLine(formatActor(actor), "Hit(type = " + hitsplat.getHitsplatType() + ", amount = " + hitsplat.getAmount() + ")", isActorConsoleLogged(actor),
                hitsCheckBox);
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event) {
        Actor sourceActor = event.getSource();
        if (!facedActors.add(sourceActor)) return;
        latestServerTick = client.getTickCount();
        Actor targetActor = event.getTarget();
        if (sourceActor == null || isActorPositionUninitialized(sourceActor)) return;
        boolean log = sourceActor instanceof Player ? isActorConsoleLogged(sourceActor) : targetActor instanceof Player ? isActorConsoleLogged(sourceActor) :
                (isActorConsoleLogged(sourceActor) || isActorConsoleLogged(targetActor));
        addLine(formatActor(sourceActor), "FaceEntity(" + (targetActor == null ? "null" : formatActor(targetActor)) + ")",
                log, interacting);
    }

    @Subscribe
    public void onFacedDirectionChanged(FacedDirectionChanged event) {
        if (event.getDirection() == -1) return;
        Actor sourceActor = event.getSource();
        latestServerTick = client.getTickCount();
        if (!facedDirectionActors.containsKey(sourceActor)) facedDirectionActors.put(sourceActor, event.getDirection());
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event) {
        resetTrackedVariables();
        ScriptEvent scriptEvent = event.getScriptEvent();
        /* Filter out the non-server created scripts. Do note that other plugins may call CS2s, such as the quest helper plugin. */
        if (scriptEvent == null || scriptEvent.getSource() != null || scriptEvent.type() != 76) return;
        final Object[] arguments = scriptEvent.getArguments();
        final int scriptId = Integer.parseInt(arguments[0].toString());
        if (ignoredClientScripts.contains(scriptId)) return;
        final StringBuilder args = new StringBuilder();
        for (int i = 1; i < arguments.length; i++) {
            final Object argument = arguments[i];
            if (argument instanceof String) {
                args.append('"').append(argument).append('"');
            } else {
                args.append(arguments[i]);
            }
            if (i < arguments.length - 1) args.append(", ");
        }
        addLine("Local", "ClientScript(id = " + scriptId + ", arguments = [" + args + "])", true, clientScripts);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        resetTrackedVariables();
    }

    @Subscribe
    public void onScriptCallback(ScriptCallbackEvent event) {
        resetTrackedVariables();
    }

    @Subscribe
    public void onPlayerMovement(PlayerMoved event) {
        movementEvents.put(event.getPlayer(), Pair.of(event, event.getPlayer().getWorldLocation()));
        this.latestServerTick = client.getTickCount();
    }

    @Subscribe
    public void onGroundItemSpawned(ItemSpawned event) {
        final Tile tile = event.getTile();
        final TileItem item = event.getItem();
        final WorldPoint location = tile.getWorldLocation();
        final StringBuilder builder = new StringBuilder();
        builder.append("ObjAdd(item = Item(");
        builder.append("id = ").append(item.getId());
        if (item.getQuantity() != 1) builder.append(", quantity = ").append(item.getQuantity());
        builder.append("), ").append(formatLocation(location)).append(")");
        addLine("Ground item add", builder.toString(), inEventDistance(location), groundItemAdd);
    }

    @Subscribe
    public void onGroundItemDespawned(ItemDespawned event) {
        final Tile tile = event.getTile();
        final TileItem item = event.getItem();
        final WorldPoint location = tile.getWorldLocation();
        final StringBuilder builder = new StringBuilder();
        builder.append("ObjDel(item = Item(");
        builder.append("id = ").append(item.getId());
        if (item.getQuantity() != 1) builder.append(", quantity = ").append(item.getQuantity());
        builder.append("), ").append(formatLocation(location)).append(")");
        addLine("Ground item delete", builder.toString(), inEventDistance(location), groundItemDel);
    }

    @Subscribe
    public void onGroundItemQuantityChange(ItemQuantityChanged event) {
        final Tile tile = event.getTile();
        final TileItem item = event.getItem();
        final WorldPoint location = tile.getWorldLocation();
        final StringBuilder builder = new StringBuilder();
        builder.append("ObjUpdate(");
        builder.append("updatedQuantity = ").append(event.getNewQuantity()).append(", ");
        builder.append("item = Item(");
        builder.append("id = ").append(item.getId());
        if (item.getQuantity() != 1) builder.append(", quantity = ").append(event.getOldQuantity());
        builder.append("), ").append(formatLocation(location)).append(")");
        addLine("Ground item quantity update", builder.toString(), inEventDistance(location), groundItemUpdate);
    }

    @Subscribe
    public void onGetDynamicObjectForAnimationEvent(GetDynamicObjectForAnimationEvent event) {
        accessedObjectForAnimation = true;
    }

    @Subscribe
    public void onDynamicObjectAnimationChanged(DynamicObjectAnimationChanged event) {
        if (!accessedObjectForAnimation) return;
        accessedObjectForAnimation = false;
        final int id = event.getObject();
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        Tile localTile = tiles[client.getPlane()][event.getX()][event.getY()];
        if (localTile == null) return;
        if (submitObjectAnimationIfMatches(localTile.getWallObject(), event, id)) return;
        if (submitObjectAnimationIfMatches(localTile.getDecorativeObject(), event, id)) return;
        if (submitObjectAnimationIfMatches(localTile.getGroundObject(), event, id)) return;
        Optional<GameObject> gameObject =
                Arrays.stream(localTile.getGameObjects()).filter(obj -> obj != null && (obj.getHash() >>> 17 & 4294967295L) == event.getObject()).findAny();
        gameObject.ifPresent(object -> submitObjectAnimation(event, object));
    }

    private boolean submitObjectAnimationIfMatches(TileObject object, DynamicObjectAnimationChanged event, int id) {
        if (object == null) return false;
        if ((object.getHash() >>> 17 & 4294967295L) == id) {
            submitObjectAnimation(event, object);
            return true;
        }
        return false;
    }

    private void submitObjectAnimation(DynamicObjectAnimationChanged event, TileObject object) {
        final int modelRotation = object.getModelOrientation();
        final int type = object.getFlags() & 0x1F;
        int rotation = modelRotation;
        if (type == 2 || type == 6 || type == 8) {
            rotation -= 4;
        } else if (type == 7) {
            rotation = (rotation - 2 & 0x3);
        }
        final LocalPoint localPoint = LocalPoint.fromScene(event.getX(), event.getY());
        final WorldPoint location = WorldPoint.fromLocal(client, localPoint);
        addLine("Map Object Animation", "LocAnim(animation = " + event.getAnimation()
                + ", object = MapObject(id = " + object.getId() + ", type = " + type +
                ", rotation = " + rotation + ", " + formatLocation(location) + ")",
                inEventDistance(localPoint), mapObjectAnim);
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        final GraphicsObject obj = event.getGraphicsObject();
        final WorldPoint location = WorldPoint.fromLocal(client, obj.getLocation());
        final int delay = obj.getStartCycle() - client.getGameCycle();
        final int tileHeight = Perspective.getTileHeight(client, obj.getLocation(), client.getPlane());
        final StringBuilder builder = new StringBuilder();
        final int height = -(obj.getHeight() - tileHeight);
        builder.append("SpotanimSpecific(");
        builder.append("id = ").append(obj.getId()).append(", ");
        if (delay != 0) builder.append("delay = ").append(delay).append(", ");
        if (height != 0) builder.append("height = ").append(height).append(", ");
        builder.append(formatLocation(location)).append(")");
        addLine("Spotanim Specific", builder.toString(), inEventDistance(location), graphicsObjectChanges);
    }

    @Subscribe
    public void onJinglePlayed(JinglePlayed event) {
        addLine("Jingle", "Jingle(id = " + event.getJingleId() + ")", true, jingles);
    }

    @Subscribe
    public void onPlayerMenuOptionChanged(PlayerMenuOptionChanged event) {
        addLine("Player Context Menu", "PlayerOption(index = " + event.getIndex() + ", priority = " + event.isPriority()
                + ", option = " + "\"" + event.getOption() + "\")", true, playerMenuOptions);
    }

    @Subscribe
    public void onExactMoveReceived(ExactMoveEvent event) {
        final Actor actor = event.getActor();
        if (actor == null || isActorPositionUninitialized(actor)) return;
        final int currentCycle = client.getGameCycle();
        final StringBuilder exactMoveBuilder = new StringBuilder();
        final WorldPoint actorWorldLocation = actor.getWorldLocation();
        exactMoveBuilder.append("ExactMove(");
        exactMoveBuilder.append("startLocation = ")
                .append(formatLocation(actorWorldLocation.getX() - event.getExactMoveDeltaX2(), actorWorldLocation.getY() - event.getExactMoveDeltaY2(),
                        client.getPlane(), false));
        exactMoveBuilder.append(", ");
        exactMoveBuilder.append("startDelay = ").append(event.getExactMoveArrive1Cycle() - currentCycle).append(", ");
        exactMoveBuilder.append("endLocation = ")
                .append(formatLocation(actorWorldLocation.getX() - event.getExactMoveDeltaX1(), actorWorldLocation.getY() - event.getExactMoveDeltaY1(),
                        client.getPlane(), false));
        exactMoveBuilder.append(", ");
        exactMoveBuilder.append("endDelay = ").append(event.getExactMoveArrive2Cycle() - currentCycle).append(", ");
        exactMoveBuilder.append("direction = ").append(event.getExactMoveDirection()).append(")");
        addLine(formatActor(actor), exactMoveBuilder.toString(), isActorConsoleLogged(actor), exactMove);
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        final NPC npc = event.getNpc();
        final NPCComposition oldComposition = event.getOld();
        final WorldPoint actorWorldLocation = npc.getWorldLocation();
        final String coordinateString = formatLocation(actorWorldLocation);
        final String actorName =
                "Npc(" + npc.getName() + ", idx: " + npc.getIndex() + ", id: " + oldComposition.getId() + ", " + coordinateString + ")";
        addLine(actorName, "Transformation(" + npc.getComposition().getId() + ")", isActorConsoleLogged(npc), transformations);
    }

    @Subscribe
    public void onPlayerChanged(PlayerChanged event) {
        final Player player = event.getPlayer();
        final PlayerAppearance appearance = appearances.get(player);
        final PlayerAppearance newAppearance = getPlayerAppearance(player);
        appearances.put(player, newAppearance);
        final String appearanceDifferences = newAppearance.getDifferences(appearance);
        if (!appearanceDifferences.isEmpty()) {
            addLine(formatActor(player), "Appearance(" + appearanceDifferences + ")", isActorConsoleLogged(player), appearancesCheckbox);
        }
        final PlayerComposition composition = player.getPlayerComposition();
        final int transformedToNpc = composition == null ? -1 : composition.getTransformedNpcId();
        final int sanitizedTransformedId = transformedToNpc == 65535 ? -1 : transformedToNpc;
        final int latestNpcId = playerTransformations.getOrDefault(player, -1);
        /* If the cached value matches the "new" version, transformation did not occur. */
        if (latestNpcId == sanitizedTransformedId) {
            return;
        }
        playerTransformations.put(player, sanitizedTransformedId);
        addLine(formatActor(player), "Transformation(" + sanitizedTransformedId + ")", isActorConsoleLogged(player), transformations);
    }

    @Subscribe
    public void onContainerItemChanged(ContainerItemChange event) {
        final int key = ((event.getInventoryId() & 0xFFFF) << 16) | (event.getSlotId() & 0xFFFF);
        final int value = ((event.getItemId() & 0xFFFF) << 16) | (event.getQuantity() & 0xFFFF);
        if (Objects.equals(inventoryDiffs.get(key), value)) return;
        inventoryDiffs.put(key, value);
        if (latestInventoryId != event.getInventoryId()) {
            final int latestWidgetCall = client.getLatestWidgetCall();
            addLine("Inventory update", "InvComponent(interfaceId = " + (latestWidgetCall >> 16) + ", componentId = " + (latestWidgetCall & 0xFFFF) + ")",
                    true, inventoryChanges);
            latestInventoryId = event.getInventoryId();
        }
        addLine("Inventory change",
                "Inv(inventoryId = " + event.getInventoryId() + ", "
                    + "slotId = " + event.getSlotId() + ", "
                    + "itemId = " + event.getItemId() + ", "
                    + "amount = " + event.getQuantity() + ")", true, inventoryChanges);
    }

    @Subscribe
    public void onPendingSpawnUpdated(PendingSpawnUpdated event) {
        /* To get the model clip packet to function, we need to combine multiple plugins into what is essentially a state machine. */
        pendingSpawnList.add(event);
        latestServerTick = client.getTickCount();
    }

    @Subscribe
    public void onAttachedModelReceived(AttachedModelEvent event) {
        /* Always remove the combined objects to ensure valid object add/remove detection. */
        final PendingSpawnUpdated latestPendingSpawn = pendingSpawnList.isEmpty() ? null : pendingSpawnList.remove(pendingSpawnList.size() - 1);
        if (latestPendingSpawn == null) {
            log.info("Latest pending spawn is null!");
            return;
        }

        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        Tile localTile = tiles[client.getPlane()][latestPendingSpawn.getX()][latestPendingSpawn.getY()];
        /* Let's assume that any object that uses this packet is a main game object. Decorations and other objects can rarely ever be clicked, let alone this. */
        Optional<GameObject> attachedObject = Arrays.stream(localTile.getGameObjects()).filter(obj -> {
            if (obj == null) return false;
            final int rotation = obj.getOrientation().getAngle() >> 9;
            final int type = obj.getFlags() & 0x1F;
            return event.getAttachedModel() == getModel(obj, type, rotation, latestPendingSpawn.getX(), latestPendingSpawn.getY());
        }).findAny();

        if (attachedObject.isEmpty()) {
            log.info("Unable to find a matching game object for object combine.");
            return;
        }


        GameObject obj = attachedObject.get();
        WorldPoint objectLocation = obj.getWorldLocation();
        final int clientTime = client.getGameCycle();
        final int rotation = obj.getOrientation().getAngle() >> 9;
        final int type = obj.getFlags() & 0x1F;
        final int minX = event.getMinX() - latestPendingSpawn.getX();
        final int minY = event.getMinY() - latestPendingSpawn.getY();
        final int maxX = event.getMaxX() - latestPendingSpawn.getX();
        final int maxY = event.getMaxY() - latestPendingSpawn.getY();
        final int startTime = event.getAnimationCycleStart() - clientTime;
        final int endTime = event.getAnimationCycleEnd() - clientTime;
        final StringBuilder locCombineBuilder = new StringBuilder();
        locCombineBuilder.append("LocCombine(");
        locCombineBuilder.append("mapObject = MapObject(id = ").append(obj.getId()).append(", type = ").append(type)
                .append(", rotation = ").append(rotation).append(", ").append(formatLocation(objectLocation)).append("), ");
        locCombineBuilder.append("startTime = ").append(startTime).append(", ");
        locCombineBuilder.append("endTime = ").append(endTime).append(", ");
        if (rsCoordFormat.isSelected()) {
            locCombineBuilder.append("minXOffset = ").append(minX).append(", ");
            locCombineBuilder.append("maxXOffset = ").append(maxX).append(", ");
            locCombineBuilder.append("minZOffset = ").append(minY).append(", ");
            locCombineBuilder.append("maxZOffset = ").append(maxY);
        } else {
            locCombineBuilder.append("minXOffset = ").append(minX).append(", ");
            locCombineBuilder.append("maxXOffset = ").append(maxX).append(", ");
            locCombineBuilder.append("minYOffset = ").append(minY).append(", ");
            locCombineBuilder.append("maxYOffset = ").append(maxY);
        }
        locCombineBuilder.append(")");
        addLine(formatActor(event.getPlayer()), locCombineBuilder.toString(), isActorConsoleLogged(event.getPlayer()), combinedObjects);
    }

    private Model getModel(final GameObject obj, final int type, final int rotation, final int x, final int y) {
        ObjectComposition def = client.getObjectDefinition(obj.getId());
        int width;
        int length;

        if (rotation == 1 || rotation == 3) {
            width = def.getSizeY();
            length = def.getSizeX();
        } else {
            width = def.getSizeX();
            length = def.getSizeY();
        }

        int x1 = x + (width >> 1);
        int x2 = x + (width + 1 >> 1);
        int y1 = y + (length >> 1);
        int y2 = y + (length + 1 >> 1);

        int[][] heights = client.getTileHeights()[obj.getPlane()];
        int averageHeight = heights[x2][y2] + heights[x1][y2] + heights[x2][y1] + heights[x1][y1] >> 2;
        int preciseX = (x << 7) + (width << 6);
        int preciseY = (y << 7) + (length << 6);
        return def.getModel(type, rotation, heights, preciseX, averageHeight, preciseY);
    }


    /**
     * Transforms a list of slots into a list of slot ranges.
     * A list of [1, 2, 3, 8, 9, 11] would get transformed to [(1, 3), (8, 9), (11, 11)].
     */
    private List<Pair<Integer, Integer>> getSlotRanges(List<Integer> slots) {
        final List<Pair<Integer, Integer>> slotRanges = new ArrayList<>();
        int start = -1;
        int last = -1;
        Collections.sort(slots);
        for (int slot : slots) {
            if (start == -1) {
                start = slot;
                last = slot;
                continue;
            }
            if (slot == last + 1) {
                last++;
                continue;
            }
            slotRanges.add(Pair.of(start, last));
            start = -1;
            last = -1;
        }
        if (start != -1) slotRanges.add(Pair.of(start, last));
        return slotRanges;
    }

    /**
     * It is possible for some variables to be uninitialized on login, so as an uber cheap fix, let's try-catch validate if the actor is fully initialized.
     */
    private boolean isActorPositionUninitialized(Actor actor) {
        try {
            return actor.getWorldLocation() == null;
        } catch (NullPointerException ignored) {
            return true;
        }
    }

    private String formatActor(@NotNull Actor actor) {
        WorldPoint actorWorldLocation = actor.getWorldLocation();
        return formatActor(actor, actorWorldLocation);
    }

    private String formatActor(@NotNull Actor actor, final WorldPoint actorWorldLocation) {
        String coordinateString = formatLocationOmitDecorations(actorWorldLocation);
        if (actor instanceof Player) {
            return ("Player(" + (actor.getName() + ", idx: " + ((Player) actor).getPlayerId() + ", " + coordinateString + ")"));
        } else if (actor instanceof NPC) {
            return ("Npc(" + (actor.getName() + ", idx: " + ((NPC) actor).getIndex() + ", id: " + ((NPC) actor).getComposition().getId() + ", " + coordinateString + ")"));
        }
        return ("Unknown(" + coordinateString + ")");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void resetOutputFile() {
        File folder = new File(System.getProperty("user.home") + "/.openosrs/event-inspector-logs/");
        folder.mkdirs();
        outputFile =
                new File(folder, new SimpleDateFormat("yyyy-MM-dd HH-mm-sss").format(new Date()) + ".txt");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN && !eventBuffer.isEmpty()) {
            writeToFile();
        }
    }

    private void writeSettingsFile() {
        try {
            synchronized (settingsFile) {
                BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile));
                for (JCheckBox setting : allSettings) {
                    writer.write(setting.getText() + "=" + setting.isSelected());
                    writer.newLine();
                }
                final Dimension size = this.getSize();
                writer.write("height=" + size.getHeight());
                writer.newLine();
                writer.write("width=" + size.getWidth());
                writer.newLine();
                writer.write("interval=" + writeInterval);
                writer.newLine();
                writer.flush();
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readSettingsFile() {
        try {
            synchronized (settingsFile) {
                BufferedReader reader = new BufferedReader(new FileReader(settingsFile));
                String line;
                double height = -1;
                double width = -1;
                while ((line = reader.readLine()) != null) {
                    String[] split = line.split("=");
                    String label = split[0];
                    switch (label) {
                        case "height":
                            height = Double.parseDouble(split[1]);
                            continue;
                        case "width":
                            width = Double.parseDouble(split[1]);
                            continue;
                        case "interval":
                            writeInterval = Integer.parseInt(split[1]);
                            continue;
                    }
                    boolean value = Boolean.parseBoolean(split[1]);
                    for (JCheckBox checkBox : allSettings) {
                        if (checkBox.getText().equals(label)) {
                            checkBox.setSelected(value);
                        }
                    }
                }
                /* Just in case, if necessary, can restore the panel's original size by making it tiny and restarting the plugin. */
                if (height > 300 && width > 500) {
                    setSize(new Dimension((int) width, (int) height));
                }
                /* Add the listener only after the saved size has been read from the file, otherwise we will override it before it gets the chance to load. */
                addComponentListener(new ComponentListener() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        writeSettingsFile();
                    }

                    @Override
                    public void componentMoved(ComponentEvent e) {}
                    @Override
                    public void componentShown(ComponentEvent e) {}
                    @Override
                    public void componentHidden(ComponentEvent e) {}
                });
                if (intervalSlider != null) {
                    intervalSlider.setValue(writeInterval);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void open() {
        resetOutputFile();
        if (settingsFile.exists()) {
            readSettingsFile();
        }
        eventBus.register(this);
        if (oldVarps == null) {
            oldVarps = new int[client.getVarps().length];
            oldVarps2 = new int[client.getVarps().length];
        }
        varbits = HashMultimap.create();

        clientThread.invoke(() -> {
            IndexDataBase indexVarbits = client.getIndexConfig();
            final int[] varbitIds = indexVarbits.getFileIds(VARBITS_ARCHIVE_ID);
            for (int id : varbitIds) {
                VarbitComposition varbit = client.getVarbit(id);
                if (varbit != null) {
                    varbits.put(varbit.getIndex(), id);
                }
            }
        });
        super.open();
    }

    @Override
    public void close() {
        super.close();
        tracker.removeAll();
        writeToFile();
        outputFile = null;
        eventBus.unregister(this);
    }

    private PlayerAppearance getPlayerAppearance(Player player) {
        final PlayerComposition composition = player.getPlayerComposition();
        return new PlayerAppearance(player.getCombatLevel(),
                composition == null ? null : composition.isFemale(),
                composition == null ? null : composition.getColors(),
                composition == null ? null : composition.getEquipmentIds(),
                composition == null ? null : composition.getTransformedNpcId(), player.getOverheadIcon(), player.getSkullIcon(),
                player.getRSSkillLevel(), player.getIsHidden(), player.getRunAnimation(), player.getWalkAnimation(), player.getWalkRotate180(),
                player.getWalkRotateLeft(), player.getWalkRotateRight(), player.getIdlePoseAnimation(), player.getIdleRotateLeft(),
                composition == null ? null : composition.getPlayerEquipmentItems());
    }

    @Data
    private static class PlayerAppearance {
        private final int combatLevel;
        private final Boolean female;
        private final int[] colors;
        private final int[] equipmentIds;
        private final Integer transformedNpcId;
        private final HeadIcon headIcon;
        private final SkullIcon skullIcon;
        private final int skillLevel;
        private final boolean hidden;
        private final int runAnim;
        private final int walkForwardAnim;
        private final int walkBackwardsAnim;
        private final int walkLeftAnim;
        private final int walkRightAnim;
        private final int standAnim;
        private final int turnOnSpotAnim;
        private final PlayerEquipmentItem[] recolouredEquipmentItems;

        public String getDifferences(PlayerAppearance previous) {
            final StringBuilder builder = new StringBuilder();
            if (previous == null || combatLevel != previous.combatLevel) {
                builder.append("combatLevel = ").append(combatLevel).append(", ");
            }
            if (previous == null || getFemale() != previous.getFemale()) {
                builder.append("female = ").append(getFemale()).append(", ");
            }
            if (previous == null || !Objects.equals(transformedNpcId, previous.transformedNpcId)) {
                builder.append("npcTransform = ").append(getTransformedNpcId()).append(", ");
            }
            if (previous == null || headIcon != previous.headIcon) {
                builder.append("headIcon = ").append(headIcon).append(", ");
            }
            if (previous == null || skullIcon != previous.skullIcon) {
                builder.append("skullIcon = ").append(skullIcon).append(", ");
            }
            if (previous == null || skillLevel != previous.skillLevel) {
                builder.append("skillLevel = ").append(skillLevel).append(", ");
            }
            if (previous == null || hidden != previous.hidden) {
                builder.append("hidden = ").append(hidden).append(", ");
            }
            if (previous == null || runAnim != previous.runAnim) builder.append("runAnim = ").append(runAnim).append(", ");
            if (previous == null || walkForwardAnim != previous.walkForwardAnim) builder.append("walkForwardAnim = ").append(walkForwardAnim).append(", ");
            if (previous == null || walkBackwardsAnim != previous.walkBackwardsAnim) builder.append("walkBackwardsAnim = ").append(walkBackwardsAnim).append(", ");
            if (previous == null || walkLeftAnim != previous.walkLeftAnim) builder.append("walkLeftAnim = ").append(walkLeftAnim).append(", ");
            if (previous == null || walkRightAnim != previous.walkRightAnim) builder.append("walkRightAnim = ").append(walkRightAnim).append(", ");
            if (previous == null || standAnim != previous.standAnim) builder.append("standAnim = ").append(standAnim).append(", ");
            if (previous == null || turnOnSpotAnim != previous.turnOnSpotAnim) builder.append("turnOnSpotAnim = ").append(turnOnSpotAnim).append(", ");
            if (previous == null || !Arrays.equals(getColors(), previous.getColors())) {
                builder.append("colours = ").append(Arrays.toString(getColors())).append(", ");
            }
            if (previous == null || !Arrays.equals(getEquipmentIds(), previous.getEquipmentIds())) {
                int[] body = new int[getEquipmentIds().length];
                int[] oldBody = new int[getEquipmentIds().length];
                int[] equipment = new int[getEquipmentIds().length];
                int[] oldEquipment = new int[getEquipmentIds().length];
                for (int i = 0; i < body.length; i++) {
                    int value = equipmentIds[i];
                    int oldValue = previous == null ? 0 : previous.equipmentIds == null ? 0 : previous.equipmentIds[i];
                    if (value < 512) {
                        body[i] = value == 0 ? 0 : (value - 256);
                    } else {
                        equipment[i] = value - 512;
                    }
                    if (oldValue < 512) {
                        oldBody[i] = oldValue == 0 ? 0 : (oldValue - 256);
                    } else {
                        oldEquipment[i] = oldValue - 512;
                    }
                }
                if (!Arrays.equals(body, oldBody)) {
                    builder.append("body = ").append(Arrays.toString(body)).append(", ");
                }
                if (!Arrays.equals(equipment, oldEquipment)) {
                    builder.append("equipment = ").append(Arrays.toString(equipment)).append(", ");
                }
            }
            if (shouldFormatEquipmentColours(previous)) {
                builder.append("itemAppearance = [").append(formatEquipmentRecolours()).append("], ");
            }

            if (builder.length() >= 2) builder.delete(builder.length() - 2, builder.length());
            return builder.toString();
        }

        private boolean shouldFormatEquipmentColours(PlayerAppearance previous) {
            if (previous == null || (recolouredEquipmentItems == null && previous.recolouredEquipmentItems != null)
            || (recolouredEquipmentItems != null && previous.recolouredEquipmentItems == null)) {
                return true;
            }
            if (recolouredEquipmentItems != null) {
                for (int i = 0; i < recolouredEquipmentItems.length; i++) {
                    final PlayerEquipmentItem current = recolouredEquipmentItems[i];
                    final PlayerEquipmentItem prev = previous.recolouredEquipmentItems[i];
                    if ((current == null && prev != null) || (current != null && prev == null)) return true;
                    if (current == null) continue;
                    if (!Arrays.equals(current.getRecolorTo(), prev.getRecolorTo()) || !Arrays.equals(current.getRetextureTo(), prev.getRetextureTo())) {
                        return true;
                    }
                }
            }
            return false;
        }

        private String formatEquipmentRecolours() {
            if (recolouredEquipmentItems == null) return "null";
            final StringBuilder recolourBuilder = new StringBuilder();
            final StringBuilder retextureBuilder = new StringBuilder();
            for (int i = 0; i < recolouredEquipmentItems.length; i++) {
                if (recolouredEquipmentItems[i] != null && recolouredEquipmentItems[i].getRecolorTo() != null) {
                    recolourBuilder.append(i).append(" = ").append(Arrays.toString(recolouredEquipmentItems[i].getRecolorTo())).append(", ");
                }
                if (recolouredEquipmentItems[i] != null && recolouredEquipmentItems[i].getRetextureTo() != null) {
                    retextureBuilder.append(i).append(" = ").append(Arrays.toString(recolouredEquipmentItems[i].getRetextureTo())).append(", ");
                }
            }
            if (recolourBuilder.length() == 0 && retextureBuilder.length() == 0) return "null";
            if (recolourBuilder.length() >= 2) recolourBuilder.delete(recolourBuilder.length() - 2, recolourBuilder.length());
            if (retextureBuilder.length() >= 2) retextureBuilder.delete(retextureBuilder.length() - 2, retextureBuilder.length());
            final StringBuilder produce = new StringBuilder();
            if (recolourBuilder.length() != 0) {
                produce.append("recolour = {").append(recolourBuilder).append("}, ");
            }
            if (retextureBuilder.length() != 0) {
                produce.append("retexture = {").append(retextureBuilder).append("}, ");
            }
            if (produce.length() >= 2) produce.delete(produce.length() - 2, produce.length());
            return produce.toString();
        }
    }

    @AllArgsConstructor
    private enum MinimapState {
        Enabled(0),
        MapUnclickable(1),
        HideMap(2),
        HideCompass(3),
        HideCompassMapUnclickable(4),
        Disabled(5);
        private final int state;

        public static MinimapState getState(int state) {
            for (MinimapState minimapState : values()) {
                if (minimapState.state == state) return minimapState;
            }
            return null;
        }
    }

    @SuppressWarnings("PointlessBitwiseExpression")
    @AllArgsConstructor
    private enum InterfaceEvent {
        Continue(1 << 0),
        ClickOp1(1 << 1),
        ClickOp2(1 << 2),
        ClickOp3(1 << 3),
        ClickOp4(1 << 4),
        ClickOp5(1 << 5),
        ClickOp6(1 << 6),
        ClickOp7(1 << 7),
        ClickOp8(1 << 8),
        ClickOp9(1 << 9),
        ClickOp10(1 << 10),
        UseOnGroundItem(1 << 11),
        UseOnNpc(1 << 12),
        UseOnObject(1 << 13),
        UseOnPlayer(1 << 14),
        UseOnInventory(1 << 15),
        UseOnComponent(1 << 16),
        DragDepth1(1 << 17),
        DragDepth2(2 << 17),
        DragDepth3(3 << 17),
        DragDepth4(4 << 17),
        DragDepth5(5 << 17),
        DragDepth6(6 << 17),
        DragDepth7(7 << 17),
        DragTargetable(1 << 20),
        ComponentTargetable(1 << 21);

        private final int value;

        private static String sanitize(final int packedEvents) {
            final StringBuilder events = new StringBuilder();
            for (InterfaceEvent event : values()) {
                if ((packedEvents & event.value) != event.value) continue;
                events.append(event).append(", ");
            }
            if (events.length() >= 2) {
                events.delete(events.length() - 2, events.length());
            }
            return events.toString();
        }
    }
}