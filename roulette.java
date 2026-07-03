// roulette.java
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class roulette {
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[92m";
    private static final String RED = "\u001B[91m";
    private static final String YELLOW = "\u001B[93m";
    private static final String BLUE = "\u001B[94m";
    private static final String CYAN = "\u001B[96m";
    private static final String BOLD = "\u001B[1m";

    private static String colorize(String text, String color) {
        return color + text + RESET;
    }

    private static class Stats {
        int games, wins, losses, score;
    }

    private String mode;
    private int playersCount;
    private int bullets;
    private int chambers;
    private double delay;
    private List<String> names;
    private List<Integer> chamber;
    private int position;
    private int currentPlayer;
    private List<Map.Entry<Integer, Boolean>> history = new ArrayList<>();
    private Map<String, Stats> stats = new HashMap<>();
    private String statsFile;

    public roulette(String mode, int players, int bullets, int chambers, double delay, List<String> names) {
        this.mode = mode;
        this.playersCount = players;
        this.bullets = bullets;
        this.chambers = chambers;
        this.delay = delay;
        this.names = names != null ? names : IntStream.range(1, players+1).mapToObj(i -> "Игрок " + i).collect(Collectors.toList());
        statsFile = System.getProperty("user.home") + "/.roulette_stats.json";
        loadStats();
    }

    private void loadStats() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(statsFile)));
            // Упрощённый парсинг JSON (без библиотеки)
            // Предполагаем формат: {"name":{"games":0,"wins":0,"losses":0,"score":0}}
            // Для простоты используем ручной разбор.
            // В реальном проекте лучше использовать Gson или Jackson.
            // Пропускаем для демонстрации.
        } catch (Exception e) {}
    }

    private void saveStats() {
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Stats> e : stats.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(e.getKey()).append("\":{");
                sb.append("\"games\":").append(e.getValue().games).append(",");
                sb.append("\"wins\":").append(e.getValue().wins).append(",");
                sb.append("\"losses\":").append(e.getValue().losses).append(",");
                sb.append("\"score\":").append(e.getValue().score);
                sb.append("}");
            }
            sb.append("}");
            Files.write(Paths.get(statsFile), sb.toString().getBytes());
        } catch (IOException e) {}
    }

    public void displayStats() {
        if (stats.isEmpty()) {
            System.out.println(colorize("Статистика пуста.", YELLOW));
            return;
        }
        for (Map.Entry<String, Stats> e : stats.entrySet()) {
            System.out.println(colorize(e.getKey() + ":", BOLD));
            System.out.printf("  Игр: %d, Побед: %d, Поражений: %d, Очки: %d\n",
                e.getValue().games, e.getValue().wins, e.getValue().losses, e.getValue().score);
        }
    }

    private void loadChamber() {
        chamber = new ArrayList<>(Collections.nCopies(chambers, 0));
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < chambers; i++) positions.add(i);
        Collections.shuffle(positions);
        for (int i = 0; i < bullets; i++) {
            chamber.set(positions.get(i), 1);
        }
        position = 0;
    }

    private void spin() {
        position = new Random().nextInt(chambers);
    }

    private boolean fire(int player) {
        if (chamber.get(position) == 1) {
            history.add(new AbstractMap.SimpleEntry<>(player, true));
            return true;
        } else {
            history.add(new AbstractMap.SimpleEntry<>(player, false));
            position = (position + 1) % chambers;
            return false;
        }
    }

    private void updateStats(int player, boolean win, int extra) {
        String name = names.get(player);
        Stats st = stats.get(name);
        if (st == null) {
            st = new Stats();
            stats.put(name, st);
        }
        st.games++;
        if (win) st.wins++;
        else st.losses++;
        st.score += extra;
        saveStats();
    }

    private boolean allPlayersShot() {
        if (history.size() < playersCount) return false;
        Set<Integer> last = new HashSet<>();
        for (int i = history.size() - playersCount; i < history.size(); i++) {
            last.add(history.get(i).getKey());
        }
        return last.size() == playersCount;
    }

    private void playRound() throws IOException {
        Scanner scanner = new Scanner(System.in);
        if (mode.equals("classic")) {
            loadChamber();
            position = 0;
            while (true) {
                int player = currentPlayer;
                System.out.println(colorize("\nХод игрока " + names.get(player), CYAN));
                System.out.print("Нажмите Enter, чтобы выстрелить...");
                scanner.nextLine();
                if (delay > 0) try { Thread.sleep((long)(delay * 1000)); } catch (InterruptedException e) {}
                boolean shot = fire(player);
                if (shot) {
                    System.out.println(colorize("💥 БАХ! " + names.get(player) + " проиграл!", RED));
                    updateStats(player, false, 0);
                    for (int i = 0; i < playersCount; i++) {
                        if (i != player) updateStats(i, true, 0);
                    }
                    return;
                } else {
                    System.out.println(colorize("Щелчок...", YELLOW));
                    currentPlayer = (currentPlayer + 1) % playersCount;
                    if (allPlayersShot()) {
                        System.out.println(colorize("Все выжили! Ничья.", BLUE));
                        for (int i = 0; i < playersCount; i++) updateStats(i, false, 0);
                        return;
                    }
                }
            }
        } else if (mode.equals("spin")) {
            loadChamber();
            while (true) {
                int player = currentPlayer;
                System.out.println(colorize("\nХод игрока " + names.get(player), CYAN));
                spin();
                System.out.print("Нажмите Enter, чтобы выстрелить...");
                scanner.nextLine();
                if (delay > 0) try { Thread.sleep((long)(delay * 1000)); } catch (InterruptedException e) {}
                boolean shot = fire(player);
                if (shot) {
                    System.out.println(colorize("💥 БАХ! " + names.get(player) + " проиграл!", RED));
                    updateStats(player, false, 0);
                    for (int i = 0; i < playersCount; i++) {
                        if (i != player) updateStats(i, true, 0);
                    }
                    return;
                } else {
                    System.out.println(colorize("Щелчок...", YELLOW));
                    currentPlayer = (currentPlayer + 1) % playersCount;
                    if (allPlayersShot()) {
                        System.out.println(colorize("Все выжили! Ничья.", BLUE));
                        for (int i = 0; i < playersCount; i++) updateStats(i, false, 0);
                        return;
                    }
                }
            }
        } else if (mode.equals("duel")) {
            loadChamber();
            position = 0;
            while (true) {
                int player = currentPlayer;
                System.out.println(colorize("\nДуэль! Ход " + names.get(player), CYAN));
                System.out.print("Нажмите Enter, чтобы выстрелить...");
                scanner.nextLine();
                if (delay > 0) try { Thread.sleep((long)(delay * 1000)); } catch (InterruptedException e) {}
                boolean shot = fire(player);
                if (shot) {
                    System.out.println(colorize("💥 " + names.get(player) + " проиграл дуэль!", RED));
                    updateStats(player, false, 0);
                    int other = 1 - player;
                    updateStats(other, true, 0);
                    return;
                } else {
                    System.out.println(colorize("Щелчок...", YELLOW));
                    currentPlayer = (currentPlayer + 1) % playersCount;
                }
            }
        } else if (mode.equals("survival")) {
            loadChamber();
            position = 0;
            int player = 0;
            int score = 0;
            System.out.println(colorize("\nРежим выживания для " + names.get(player), BLUE));
            while (true) {
                System.out.println(colorize("Текущий счёт: " + score, YELLOW));
                System.out.print("Выстрелить? (y/n): ");
                String choice = scanner.nextLine().trim().toLowerCase();
                if (choice.equals("q")) {
                    System.out.println("Выход.");
                    return;
                }
                if (choice.equals("n")) {
                    System.out.println(colorize("Вы остановились с " + score + " очками.", GREEN));
                    updateStats(player, true, score);
                    return;
                }
                if (choice.equals("y")) {
                    if (delay > 0) try { Thread.sleep((long)(delay * 1000)); } catch (InterruptedException e) {}
                    boolean shot = fire(player);
                    if (shot) {
                        System.out.println(colorize("💥 БАХ! Вы проиграли! Очки: " + score, RED));
                        updateStats(player, false, score);
                        return;
                    } else {
                        System.out.println(colorize("Щелчок...", YELLOW));
                        score++;
                        position = (position + 1) % chambers;
                    }
                }
            }
        }
        scanner.close();
    }

    public void play() throws IOException {
        System.out.println(colorize("🔫 Добро пожаловать в русскую рулетку!", BOLD));
        System.out.printf("Режим: %s, Игроков: %d, Патронов: %d, Гнёзд: %d\n", mode, playersCount, bullets, chambers);
        if (delay > 0) System.out.printf("Задержка: %d мс\n", (int)(delay * 1000));
        System.out.println("Имена: " + String.join(", ", names));
        System.out.println();
        playRound();
    }

    public static void main(String[] args) throws IOException {
        String mode = "classic";
        int players = 2, bullets = 1, chambers = 6;
        double delay = 0.2;
        List<String> names = null;
        boolean showStats = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("classic") || arg.equals("spin") || arg.equals("duel") || arg.equals("survival")) {
                mode = arg;
            } else if (arg.equals("-p") && i+1 < args.length) {
                players = Integer.parseInt(args[++i]);
            } else if (arg.equals("-b") && i+1 < args.length) {
                bullets = Integer.parseInt(args[++i]);
            } else if (arg.equals("-c") && i+1 < args.length) {
                chambers = Integer.parseInt(args[++i]);
            } else if (arg.equals("-n") && i+1 < args.length) {
                names = Arrays.asList(args[++i].split(","));
            } else if (arg.equals("-d") && i+1 < args.length) {
                delay = Double.parseDouble(args[++i]);
            } else if (arg.equals("-s")) {
                showStats = true;
            } else if (arg.equals("-h")) {
                System.out.println("Usage: java roulette [mode] [-p players] [-b bullets] [-c chambers] [-n names] [-d delay] [-s]");
                return;
            }
        }
        if (names == null) {
            names = new ArrayList<>();
            for (int i = 1; i <= players; i++) names.add("Игрок " + i);
        }
        roulette game = new roulette(mode, players, bullets, chambers, delay, names);
        if (showStats) {
            game.displayStats();
            return;
        }
        game.play();
    }
}
