// roulette.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Threading;
using System.Linq;

class Roulette
{
    static string Colorize(string text, string color)
    {
        string col = color switch
        {
            "green" => "\x1b[92m",
            "red" => "\x1b[91m",
            "yellow" => "\x1b[93m",
            "blue" => "\x1b[94m",
            "cyan" => "\x1b[96m",
            "bold" => "\x1b[1m",
            _ => "\x1b[0m"
        };
        return col + text + "\x1b[0m";
    }

    private string mode;
    private int playersCount;
    private int bullets;
    private int chambers;
    private double delay;
    private List<string> names;
    private List<int> chamber;
    private int position;
    private int currentPlayer;
    private List<(int player, bool shot)> history = new List<(int, bool)>();
    private Dictionary<string, Stats> stats;
    private string statsFile;

    class Stats
    {
        public int games { get; set; }
        public int wins { get; set; }
        public int losses { get; set; }
        public int score { get; set; }
    }

    public Roulette(string mode, int players, int bullets, int chambers, double delay, List<string> names)
    {
        this.mode = mode;
        this.playersCount = players;
        this.bullets = bullets;
        this.chambers = chambers;
        this.delay = delay;
        this.names = names ?? Enumerable.Range(1, players).Select(i => $"Игрок {i}").ToList();
        statsFile = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".roulette_stats.json");
        LoadStats();
    }

    void LoadStats()
    {
        if (File.Exists(statsFile))
        {
            string json = File.ReadAllText(statsFile);
            stats = JsonSerializer.Deserialize<Dictionary<string, Stats>>(json) ?? new Dictionary<string, Stats>();
        }
        else stats = new Dictionary<string, Stats>();
    }

    void SaveStats()
    {
        string json = JsonSerializer.Serialize(stats);
        File.WriteAllText(statsFile, json);
    }

    public void DisplayStats()
    {
        if (stats.Count == 0)
        {
            Console.WriteLine(Colorize("Статистика пуста.", "yellow"));
            return;
        }
        foreach (var kv in stats)
        {
            Console.WriteLine(Colorize(kv.Key + ":", "bold"));
            Console.WriteLine($"  Игр: {kv.Value.games}, Побед: {kv.Value.wins}, Поражений: {kv.Value.losses}, Очки: {kv.Value.score}");
        }
    }

    void LoadChamber()
    {
        chamber = new List<int>(new int[chambers]);
        var positions = Enumerable.Range(0, chambers).ToList();
        Random rnd = new Random();
        for (int i = positions.Count - 1; i > 0; i--)
        {
            int j = rnd.Next(i + 1);
            int tmp = positions[i];
            positions[i] = positions[j];
            positions[j] = tmp;
        }
        for (int i = 0; i < bullets; i++) chamber[positions[i]] = 1;
        position = 0;
    }

    void Spin()
    {
        Random rnd = new Random();
        position = rnd.Next(chambers);
    }

    bool Fire(int player)
    {
        if (chamber[position] == 1)
        {
            history.Add((player, true));
            return true;
        }
        else
        {
            history.Add((player, false));
            position = (position + 1) % chambers;
            return false;
        }
    }

    void UpdateStats(int player, bool win, int extra = 0)
    {
        string name = names[player];
        if (!stats.ContainsKey(name)) stats[name] = new Stats();
        stats[name].games++;
        if (win) stats[name].wins++;
        else stats[name].losses++;
        stats[name].score += extra;
        SaveStats();
    }

    bool AllPlayersShot()
    {
        if (history.Count < playersCount) return false;
        var last = new HashSet<int>();
        for (int i = history.Count - playersCount; i < history.Count; i++)
            last.Add(history[i].player);
        return last.Count == playersCount;
    }

    void PlayRound()
    {
        if (mode == "classic")
        {
            LoadChamber();
            position = 0;
            while (true)
            {
                int player = currentPlayer;
                Console.WriteLine(Colorize($"\nХод игрока {names[player]}", "cyan"));
                Console.Write("Нажмите Enter, чтобы выстрелить...");
                Console.ReadLine();
                if (delay > 0) Thread.Sleep((int)(delay * 1000));
                bool shot = Fire(player);
                if (shot)
                {
                    Console.WriteLine(Colorize($"💥 БАХ! {names[player]} проиграл!", "red"));
                    UpdateStats(player, false);
                    for (int i = 0; i < playersCount; i++)
                        if (i != player) UpdateStats(i, true);
                    return;
                }
                else
                {
                    Console.WriteLine(Colorize("Щелчок...", "yellow"));
                    currentPlayer = (currentPlayer + 1) % playersCount;
                    if (AllPlayersShot())
                    {
                        Console.WriteLine(Colorize("Все выжили! Ничья.", "blue"));
                        for (int i = 0; i < playersCount; i++) UpdateStats(i, false);
                        return;
                    }
                }
            }
        }
        else if (mode == "spin")
        {
            LoadChamber();
            while (true)
            {
                int player = currentPlayer;
                Console.WriteLine(Colorize($"\nХод игрока {names[player]}", "cyan"));
                Spin();
                Console.Write("Нажмите Enter, чтобы выстрелить...");
                Console.ReadLine();
                if (delay > 0) Thread.Sleep((int)(delay * 1000));
                bool shot = Fire(player);
                if (shot)
                {
                    Console.WriteLine(Colorize($"💥 БАХ! {names[player]} проиграл!", "red"));
                    UpdateStats(player, false);
                    for (int i = 0; i < playersCount; i++)
                        if (i != player) UpdateStats(i, true);
                    return;
                }
                else
                {
                    Console.WriteLine(Colorize("Щелчок...", "yellow"));
                    currentPlayer = (currentPlayer + 1) % playersCount;
                    if (AllPlayersShot())
                    {
                        Console.WriteLine(Colorize("Все выжили! Ничья.", "blue"));
                        for (int i = 0; i < playersCount; i++) UpdateStats(i, false);
                        return;
                    }
                }
            }
        }
        else if (mode == "duel")
        {
            LoadChamber();
            position = 0;
            while (true)
            {
                int player = currentPlayer;
                Console.WriteLine(Colorize($"\nДуэль! Ход {names[player]}", "cyan"));
                Console.Write("Нажмите Enter, чтобы выстрелить...");
                Console.ReadLine();
                if (delay > 0) Thread.Sleep((int)(delay * 1000));
                bool shot = Fire(player);
                if (shot)
                {
                    Console.WriteLine(Colorize($"💥 {names[player]} проиграл дуэль!", "red"));
                    UpdateStats(player, false);
                    int other = 1 - player;
                    UpdateStats(other, true);
                    return;
                }
                else
                {
                    Console.WriteLine(Colorize("Щелчок...", "yellow"));
                    currentPlayer = (currentPlayer + 1) % playersCount;
                }
            }
        }
        else if (mode == "survival")
        {
            LoadChamber();
            position = 0;
            int player = 0;
            int score = 0;
            Console.WriteLine(Colorize($"\nРежим выживания для {names[player]}", "blue"));
            while (true)
            {
                Console.WriteLine(Colorize($"Текущий счёт: {score}", "yellow"));
                Console.Write("Выстрелить? (y/n): ");
                string choice = Console.ReadLine().Trim().ToLower();
                if (choice == "q") { Console.WriteLine("Выход."); return; }
                if (choice == "n")
                {
                    Console.WriteLine(Colorize($"Вы остановились с {score} очками.", "green"));
                    UpdateStats(player, true, score);
                    return;
                }
                if (choice == "y")
                {
                    if (delay > 0) Thread.Sleep((int)(delay * 1000));
                    bool shot = Fire(player);
                    if (shot)
                    {
                        Console.WriteLine(Colorize($"💥 БАХ! Вы проиграли! Очки: {score}", "red"));
                        UpdateStats(player, false, score);
                        return;
                    }
                    else
                    {
                        Console.WriteLine(Colorize("Щелчок...", "yellow"));
                        score++;
                        position = (position + 1) % chambers;
                    }
                }
            }
        }
    }

    public void Play()
    {
        Console.WriteLine(Colorize("🔫 Добро пожаловать в русскую рулетку!", "bold"));
        Console.WriteLine($"Режим: {mode}, Игроков: {playersCount}, Патронов: {bullets}, Гнёзд: {chambers}");
        if (delay > 0) Console.WriteLine($"Задержка: {delay*1000} мс");
        Console.WriteLine("Имена: " + string.Join(", ", names));
        Console.WriteLine();
        PlayRound();
    }

    static void Main(string[] args)
    {
        string mode = "classic";
        int players = 2, bullets = 1, chambers = 6;
        double delay = 0.2;
        List<string> names = null;
        bool showStats = false;

        for (int i = 0; i < args.Length; i++)
        {
            string arg = args[i];
            if (arg == "classic" || arg == "spin" || arg == "duel" || arg == "survival")
                mode = arg;
            else if (arg == "-p" && i + 1 < args.Length)
                players = int.Parse(args[++i]);
            else if (arg == "-b" && i + 1 < args.Length)
                bullets = int.Parse(args[++i]);
            else if (arg == "-c" && i + 1 < args.Length)
                chambers = int.Parse(args[++i]);
            else if (arg == "-n" && i + 1 < args.Length)
                names = args[++i].Split(',').ToList();
            else if (arg == "-d" && i + 1 < args.Length)
                delay = double.Parse(args[++i]);
            else if (arg == "-s")
                showStats = true;
            else if (arg == "-h")
            {
                Console.WriteLine("Usage: roulette [mode] [-p players] [-b bullets] [-c chambers] [-n names] [-d delay] [-s]");
                return;
            }
        }
        if (names == null)
            names = Enumerable.Range(1, players).Select(i => $"Игрок {i}").ToList();
        var game = new Roulette(mode, players, bullets, chambers, delay, names);
        if (showStats)
        {
            game.DisplayStats();
            return;
        }
        game.Play();
    }
}
