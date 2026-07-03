// roulette.go
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const (
	reset  = "\033[0m"
	green  = "\033[92m"
	red    = "\033[91m"
	yellow = "\033[93m"
	blue   = "\033[94m"
	cyan   = "\033[96m"
	bold   = "\033[1m"
)

func colorize(text, color string) string {
	return color + text + reset
}

type Stats map[string]struct {
	Games  int `json:"games"`
	Wins   int `json:"wins"`
	Losses int `json:"losses"`
	Score  int `json:"score"`
}

type Roulette struct {
	mode         string
	playersCount int
	bullets      int
	chambers     int
	delay        float64
	names        []string
	chamber      []int
	position     int
	current      int
	history      []struct{ player int; shot bool }
	stats        Stats
	statsFile    string
}

func NewRoulette(mode string, players, bullets, chambers int, delay float64, names []string) *Roulette {
	if names == nil {
		names = make([]string, players)
		for i := 0; i < players; i++ {
			names[i] = fmt.Sprintf("Игрок %d", i+1)
		}
	}
	r := &Roulette{
		mode:         mode,
		playersCount: players,
		bullets:      bullets,
		chambers:     chambers,
		delay:        delay,
		names:        names,
		statsFile:    filepath.Join(os.Getenv("HOME"), ".roulette_stats.json"),
	}
	r.loadStats()
	return r
}

func (r *Roulette) loadStats() {
	data, err := os.ReadFile(r.statsFile)
	if err != nil {
		r.stats = make(Stats)
		return
	}
	if err := json.Unmarshal(data, &r.stats); err != nil {
		r.stats = make(Stats)
	}
}

func (r *Roulette) saveStats() {
	data, err := json.MarshalIndent(r.stats, "", "  ")
	if err != nil {
		return
	}
	os.WriteFile(r.statsFile, data, 0644)
}

func (r *Roulette) displayStats() {
	if len(r.stats) == 0 {
		fmt.Println(colorize("Статистика пуста.", yellow))
		return
	}
	for name, st := range r.stats {
		fmt.Println(colorize(name+":", bold))
		fmt.Printf("  Игр: %d, Побед: %d, Поражений: %d, Очки: %d\n", st.Games, st.Wins, st.Losses, st.Score)
	}
}

func (r *Roulette) loadChamber() {
	r.chamber = make([]int, r.chambers)
	positions := rand.Perm(r.chambers)
	for i := 0; i < r.bullets; i++ {
		r.chamber[positions[i]] = 1
	}
	r.position = 0
}

func (r *Roulette) spin() {
	r.position = rand.Intn(r.chambers)
}

func (r *Roulette) fire(player int) bool {
	if r.chamber[r.position] == 1 {
		r.history = append(r.history, struct{ player int; shot bool }{player, true})
		return true
	} else {
		r.history = append(r.history, struct{ player int; shot bool }{player, false})
		r.position = (r.position + 1) % r.chambers
		return false
	}
}

func (r *Roulette) updateStats(player int, win bool, extra int) {
	name := r.names[player]
	st := r.stats[name]
	st.Games++
	if win {
		st.Wins++
	} else {
		st.Losses++
	}
	st.Score += extra
	r.stats[name] = st
	r.saveStats()
}

func (r *Roulette) allPlayersShot() bool {
	if len(r.history) < r.playersCount {
		return false
	}
	last := make(map[int]bool)
	for i := len(r.history) - r.playersCount; i < len(r.history); i++ {
		last[r.history[i].player] = true
	}
	return len(last) == r.playersCount
}

func (r *Roulette) playRound() {
	scanner := bufio.NewScanner(os.Stdin)
	if r.mode == "classic" {
		r.loadChamber()
		r.position = 0
		for {
			player := r.current
			fmt.Println(colorize(fmt.Sprintf("\nХод игрока %s", r.names[player]), cyan))
			fmt.Print("Нажмите Enter, чтобы выстрелить...")
			scanner.Scan()
			if r.delay > 0 {
				time.Sleep(time.Duration(r.delay * 1000) * time.Millisecond)
			}
			shot := r.fire(player)
			if shot {
				fmt.Println(colorize(fmt.Sprintf("💥 БАХ! %s проиграл!", r.names[player]), red))
				r.updateStats(player, false, 0)
				for i := 0; i < r.playersCount; i++ {
					if i != player {
						r.updateStats(i, true, 0)
					}
				}
				return
			} else {
				fmt.Println(colorize("Щелчок...", yellow))
				r.current = (r.current + 1) % r.playersCount
				if r.allPlayersShot() {
					fmt.Println(colorize("Все выжили! Ничья.", blue))
					for i := 0; i < r.playersCount; i++ {
						r.updateStats(i, false, 0)
					}
					return
				}
			}
		}
	} else if r.mode == "spin" {
		r.loadChamber()
		for {
			player := r.current
			fmt.Println(colorize(fmt.Sprintf("\nХод игрока %s", r.names[player]), cyan))
			r.spin()
			fmt.Print("Нажмите Enter, чтобы выстрелить...")
			scanner.Scan()
			if r.delay > 0 {
				time.Sleep(time.Duration(r.delay * 1000) * time.Millisecond)
			}
			shot := r.fire(player)
			if shot {
				fmt.Println(colorize(fmt.Sprintf("💥 БАХ! %s проиграл!", r.names[player]), red))
				r.updateStats(player, false, 0)
				for i := 0; i < r.playersCount; i++ {
					if i != player {
						r.updateStats(i, true, 0)
					}
				}
				return
			} else {
				fmt.Println(colorize("Щелчок...", yellow))
				r.current = (r.current + 1) % r.playersCount
				if r.allPlayersShot() {
					fmt.Println(colorize("Все выжили! Ничья.", blue))
					for i := 0; i < r.playersCount; i++ {
						r.updateStats(i, false, 0)
					}
					return
				}
			}
		}
	} else if r.mode == "duel" {
		r.loadChamber()
		r.position = 0
		for {
			player := r.current
			fmt.Println(colorize(fmt.Sprintf("\nДуэль! Ход %s", r.names[player]), cyan))
			fmt.Print("Нажмите Enter, чтобы выстрелить...")
			scanner.Scan()
			if r.delay > 0 {
				time.Sleep(time.Duration(r.delay * 1000) * time.Millisecond)
			}
			shot := r.fire(player)
			if shot {
				fmt.Println(colorize(fmt.Sprintf("💥 %s проиграл дуэль!", r.names[player]), red))
				r.updateStats(player, false, 0)
				other := 1 - player
				r.updateStats(other, true, 0)
				return
			} else {
				fmt.Println(colorize("Щелчок...", yellow))
				r.current = (r.current + 1) % r.playersCount
			}
		}
	} else if r.mode == "survival" {
		r.loadChamber()
		r.position = 0
		player := 0
		score := 0
		fmt.Println(colorize(fmt.Sprintf("\nРежим выживания для %s", r.names[player]), blue))
		for {
			fmt.Println(colorize(fmt.Sprintf("Текущий счёт: %d", score), yellow))
			fmt.Print("Выстрелить? (y/n): ")
			scanner.Scan()
			choice := strings.ToLower(scanner.Text())
			if choice == "q" {
				fmt.Println("Выход.")
				return
			}
			if choice == "n" {
				fmt.Println(colorize(fmt.Sprintf("Вы остановились с %d очками.", score), green))
				r.updateStats(player, true, score)
				return
			}
			if choice == "y" {
				if r.delay > 0 {
					time.Sleep(time.Duration(r.delay * 1000) * time.Millisecond)
				}
				shot := r.fire(player)
				if shot {
					fmt.Println(colorize(fmt.Sprintf("💥 БАХ! Вы проиграли! Очки: %d", score), red))
					r.updateStats(player, false, score)
					return
				} else {
					fmt.Println(colorize("Щелчок...", yellow))
					score++
					r.position = (r.position + 1) % r.chambers
				}
			}
		}
	}
}

func (r *Roulette) play() {
	fmt.Println(colorize("🔫 Добро пожаловать в русскую рулетку!", bold))
	fmt.Printf("Режим: %s, Игроков: %d, Патронов: %d, Гнёзд: %d\n", r.mode, r.playersCount, r.bullets, r.chambers)
	if r.delay > 0 {
		fmt.Printf("Задержка: %d мс\n", int(r.delay*1000))
	}
	fmt.Println("Имена: " + strings.Join(r.names, ", "))
	fmt.Println()
	r.playRound()
}

func main() {
	rand.Seed(time.Now().UnixNano())
	mode := "classic"
	players := 2
	bullets := 1
	chambers := 6
	delay := 0.2
	var names []string
	showStats := false

	for i := 1; i < len(os.Args); i++ {
		arg := os.Args[i]
		switch arg {
		case "classic", "spin", "duel", "survival":
			mode = arg
		case "-p":
			if i+1 < len(os.Args) {
				players, _ = strconv.Atoi(os.Args[i+1])
				i++
			}
		case "-b":
			if i+1 < len(os.Args) {
				bullets, _ = strconv.Atoi(os.Args[i+1])
				i++
			}
		case "-c":
			if i+1 < len(os.Args) {
				chambers, _ = strconv.Atoi(os.Args[i+1])
				i++
			}
		case "-n":
			if i+1 < len(os.Args) {
				names = strings.Split(os.Args[i+1], ",")
				i++
			}
		case "-d":
			if i+1 < len(os.Args) {
				delay, _ = strconv.ParseFloat(os.Args[i+1], 64)
				i++
			}
		case "-s":
			showStats = true
		case "-h":
			fmt.Println("Usage: roulette [mode] [-p players] [-b bullets] [-c chambers] [-n names] [-d delay] [-s]")
			return
		}
	}
	if names == nil {
		for i := 0; i < players; i++ {
			names = append(names, fmt.Sprintf("Игрок %d", i+1))
		}
	}
	game := NewRoulette(mode, players, bullets, chambers, delay, names)
	if showStats {
		game.displayStats()
		return
	}
	game.play()
}
