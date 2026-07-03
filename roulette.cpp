// roulette.cpp
#include <iostream>
#include <vector>
#include <string>
#include <random>
#include <chrono>
#include <thread>
#include <fstream>
#include <sstream>
#include <map>
#include <algorithm>
#include <cctype>

using namespace std;

const string RESET = "\033[0m";
const string GREEN = "\033[92m";
const string RED = "\033[91m";
const string YELLOW = "\033[93m";
const string BLUE = "\033[94m";
const string CYAN = "\033[96m";
const string BOLD = "\033[1m";

string colorize(const string& text, const string& color) {
    return color + text + RESET;
}

string getHomeDir() {
    const char* home = getenv("HOME");
    if (!home) home = getenv("USERPROFILE");
    return string(home);
}

class Roulette {
public:
    string mode;
    int players_count;
    int bullets;
    int chambers;
    double delay;
    vector<string> names;
    vector<int> chamber;
    int position;
    int current_player;
    vector<pair<int,bool>> history;
    map<string, map<string,int>> stats;
    string stats_file;

    Roulette(string m, int p, int b, int c, double d, vector<string> n)
        : mode(m), players_count(p), bullets(b), chambers(c), delay(d), names(n) {
        if (names.empty()) {
            for (int i=0; i<players_count; ++i)
                names.push_back("Игрок " + to_string(i+1));
        }
        stats_file = getHomeDir() + "/.roulette_stats.json";
        loadStats();
    }

    void loadStats() {
        ifstream f(stats_file);
        if (!f) return;
        string line;
        while (getline(f, line)) {
            // Упрощённый парсинг JSON (для демонстрации)
            // Будем считать, что формат: {"name":{"games":0,"wins":0,"losses":0,"score":0}}
            // Для простоты пропустим, оставим пустым.
        }
    }

    void saveStats() {
        ofstream f(stats_file);
        if (!f) return;
        f << "{";
        bool first = true;
        for (auto& kv : stats) {
            if (!first) f << ",";
            first = false;
            f << "\"" << kv.first << "\":{";
            f << "\"games\":" << kv.second["games"] << ",";
            f << "\"wins\":" << kv.second["wins"] << ",";
            f << "\"losses\":" << kv.second["losses"] << ",";
            f << "\"score\":" << kv.second["score"] << "}";
        }
        f << "}";
    }

    void displayStats() {
        if (stats.empty()) {
            cout << colorize("Статистика пуста.", YELLOW) << endl;
            return;
        }
        for (auto& kv : stats) {
            cout << colorize(kv.first + ":", BOLD) << endl;
            cout << "  Игр: " << kv.second["games"] << ", Побед: " << kv.second["wins"]
                 << ", Поражений: " << kv.second["losses"] << ", Очки: " << kv.second["score"] << endl;
        }
    }

    void loadChamber() {
        chamber.assign(chambers, 0);
        random_device rd;
        mt19937 gen(rd());
        vector<int> pos(chambers);
        iota(pos.begin(), pos.end(), 0);
        shuffle(pos.begin(), pos.end(), gen);
        for (int i=0; i<bullets; ++i) {
            chamber[pos[i]] = 1;
        }
        position = 0;
    }

    void spin() {
        random_device rd;
        mt19937 gen(rd());
        uniform_int_distribution<> dis(0, chambers-1);
        position = dis(gen);
    }

    bool fire(int player) {
        if (chamber[position] == 1) {
            history.push_back({player, true});
            return true;
        } else {
            history.push_back({player, false});
            position = (position + 1) % chambers;
            return false;
        }
    }

    void updateStats(int player, bool win, int extra=0) {
        string name = names[player];
        if (stats.find(name) == stats.end()) {
            stats[name]["games"] = 0;
            stats[name]["wins"] = 0;
            stats[name]["losses"] = 0;
            stats[name]["score"] = 0;
        }
        stats[name]["games"]++;
        if (win) stats[name]["wins"]++;
        else stats[name]["losses"]++;
        stats[name]["score"] += extra;
        saveStats();
    }

    bool allPlayersShot() {
        if ((int)history.size() < players_count) return false;
        set<int> last;
        for (int i = history.size()-players_count; i < (int)history.size(); ++i) {
            last.insert(history[i].first);
        }
        return last.size() == (size_t)players_count;
    }

    void playRound() {
        if (mode == "classic") {
            loadChamber();
            position = 0;
            while (true) {
                int player = current_player;
                cout << colorize("\nХод игрока " + names[player], CYAN) << endl;
                cout << "Нажмите Enter, чтобы выстрелить...";
                cin.ignore();
                if (delay > 0) this_thread::sleep_for(chrono::milliseconds((int)(delay*1000)));
                bool shot = fire(player);
                if (shot) {
                    cout << colorize("💥 БАХ! " + names[player] + " проиграл!", RED) << endl;
                    updateStats(player, false);
                    for (int i=0; i<players_count; ++i) {
                        if (i != player) updateStats(i, true);
                    }
                    return;
                } else {
                    cout << colorize("Щелчок...", YELLOW) << endl;
                    current_player = (current_player + 1) % players_count;
                    if (allPlayersShot()) {
                        cout << colorize("Все выжили! Ничья.", BLUE) << endl;
                        for (int i=0; i<players_count; ++i) updateStats(i, false);
                        return;
                    }
                }
            }
        } else if (mode == "spin") {
            loadChamber();
            while (true) {
                int player = current_player;
                cout << colorize("\nХод игрока " + names[player], CYAN) << endl;
                spin();
                cout << "Нажмите Enter, чтобы выстрелить...";
                cin.ignore();
                if (delay > 0) this_thread::sleep_for(chrono::milliseconds((int)(delay*1000)));
                bool shot = fire(player);
                if (shot) {
                    cout << colorize("💥 БАХ! " + names[player] + " проиграл!", RED) << endl;
                    updateStats(player, false);
                    for (int i=0; i<players_count; ++i) {
                        if (i != player) updateStats(i, true);
                    }
                    return;
                } else {
                    cout << colorize("Щелчок...", YELLOW) << endl;
                    current_player = (current_player + 1) % players_count;
                    if (allPlayersShot()) {
                        cout << colorize("Все выжили! Ничья.", BLUE) << endl;
                        for (int i=0; i<players_count; ++i) updateStats(i, false);
                        return;
                    }
                }
            }
        } else if (mode == "duel") {
            loadChamber();
            position = 0;
            while (true) {
                int player = current_player;
                cout << colorize("\nДуэль! Ход " + names[player], CYAN) << endl;
                cout << "Нажмите Enter, чтобы выстрелить...";
                cin.ignore();
                if (delay > 0) this_thread::sleep_for(chrono::milliseconds((int)(delay*1000)));
                bool shot = fire(player);
                if (shot) {
                    cout << colorize("💥 " + names[player] + " проиграл дуэль!", RED) << endl;
                    updateStats(player, false);
                    int other = 1 - player;
                    updateStats(other, true);
                    return;
                } else {
                    cout << colorize("Щелчок...", YELLOW) << endl;
                    current_player = (current_player + 1) % players_count;
                }
            }
        } else if (mode == "survival") {
            loadChamber();
            position = 0;
            int player = 0;
            int round_score = 0;
            cout << colorize("\nРежим выживания для " + names[player], BLUE) << endl;
            while (true) {
                cout << colorize("Текущий счёт: " + to_string(round_score), YELLOW) << endl;
                cout << "Выстрелить? (y/n): ";
                string choice;
                cin >> choice;
                if (choice == "q") { cout << "Выход." << endl; return; }
                if (choice == "n") {
                    cout << colorize("Вы остановились с " + to_string(round_score) + " очками.", GREEN) << endl;
                    updateStats(player, true, round_score);
                    return;
                }
                if (choice == "y") {
                    if (delay > 0) this_thread::sleep_for(chrono::milliseconds((int)(delay*1000)));
                    bool shot = fire(player);
                    if (shot) {
                        cout << colorize("💥 БАХ! Вы проиграли! Очки: " + to_string(round_score), RED) << endl;
                        updateStats(player, false, round_score);
                        return;
                    } else {
                        cout << colorize("Щелчок...", YELLOW) << endl;
                        round_score++;
                        position = (position + 1) % chambers;
                    }
                }
            }
        }
    }

    void play() {
        cout << colorize("🔫 Добро пожаловать в русскую рулетку!", BOLD) << endl;
        cout << "Режим: " << mode << ", Игроков: " << players_count << ", Патронов: " << bullets << ", Гнёзд: " << chambers << endl;
        if (delay > 0) cout << "Задержка: " << delay*1000 << " мс" << endl;
        cout << "Имена: ";
        for (auto& n : names) cout << n << " ";
        cout << endl << endl;
        playRound();
    }
};

int main(int argc, char* argv[]) {
    string mode = "classic";
    int players = 2, bullets = 1, chambers = 6;
    double delay = 0.2;
    vector<string> names;
    bool showStats = false;

    for (int i=1; i<argc; ++i) {
        string arg = argv[i];
        if (arg == "classic" || arg == "spin" || arg == "duel" || arg == "survival") {
            mode = arg;
        } else if (arg == "-p" && i+1 < argc) {
            players = stoi(argv[++i]);
        } else if (arg == "-b" && i+1 < argc) {
            bullets = stoi(argv[++i]);
        } else if (arg == "-c" && i+1 < argc) {
            chambers = stoi(argv[++i]);
        } else if (arg == "-n" && i+1 < argc) {
            string namesStr = argv[++i];
            stringstream ss(namesStr);
            string name;
            while (getline(ss, name, ',')) {
                names.push_back(name);
            }
        } else if (arg == "-d" && i+1 < argc) {
            delay = stod(argv[++i]);
        } else if (arg == "-s") {
            showStats = true;
        } else if (arg == "-h") {
            cout << "Usage: roulette [mode] [-p players] [-b bullets] [-c chambers] [-n names] [-d delay] [-s]" << endl;
            return 0;
        }
    }
    if (names.empty()) {
        for (int i=0; i<players; ++i) names.push_back("Игрок " + to_string(i+1));
    }
    Roulette game(mode, players, bullets, chambers, delay, names);
    if (showStats) {
        game.displayStats();
        return 0;
    }
    game.play();
    return 0;
}
