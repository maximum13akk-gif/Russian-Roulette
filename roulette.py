# roulette.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os
import random
import json
import time
import argparse
from pathlib import Path

# ANSI-цвета
COLORS = {
    'reset': '\033[0m',
    'green': '\033[92m',
    'red': '\033[91m',
    'yellow': '\033[93m',
    'blue': '\033[94m',
    'cyan': '\033[96m',
    'bold': '\033[1m'
}

def colorize(text, color):
    return f"{COLORS.get(color, '')}{text}{COLORS['reset']}"

class Roulette:
    def __init__(self, mode, players=2, bullets=1, chambers=6, names=None, delay=0.2):
        self.mode = mode
        self.players_count = players
        self.bullets = bullets
        self.chambers = chambers
        self.delay = delay
        self.names = names or [f"Игрок {i+1}" for i in range(players)]
        self.chamber = [0] * chambers  # 0 - пусто, 1 - патрон
        self.position = 0
        self.current_player = 0
        self.history = []
        self.stats_file = Path.home() / '.roulette_stats.json'
        self.stats = self.load_stats()

    def load_stats(self):
        if self.stats_file.exists():
            with open(self.stats_file, 'r') as f:
                return json.load(f)
        return {}

    def save_stats(self):
        with open(self.stats_file, 'w') as f:
            json.dump(self.stats, f, indent=2)

    def display_stats(self):
        if not self.stats:
            print(colorize("Статистика пуста.", 'yellow'))
            return
        for name, data in self.stats.items():
            total = data.get('games', 0)
            wins = data.get('wins', 0)
            losses = data.get('losses', 0)
            if total == 0:
                continue
            win_rate = (wins / total) * 100
            print(colorize(f"{name}:", 'bold'))
            print(f"  Игр: {total}, Побед: {wins}, Поражений: {losses}, Процент побед: {win_rate:.1f}%")

    def load_chamber(self):
        # Заполняем барабан: случайно размещаем патроны
        self.chamber = [0] * self.chambers
        positions = random.sample(range(self.chambers), self.bullets)
        for pos in positions:
            self.chamber[pos] = 1
        self.position = 0

    def spin(self):
        self.position = random.randint(0, self.chambers - 1)

    def fire(self, player_idx):
        # Проверяем, есть ли патрон в текущей позиции
        if self.chamber[self.position] == 1:
            # Выстрел!
            self.history.append((player_idx, True))
            return True
        else:
            self.history.append((player_idx, False))
            self.position = (self.position + 1) % self.chambers
            return False

    def play_round(self):
        # Один раунд игры (может состоять из нескольких ходов)
        if self.mode == 'classic':
            self.load_chamber()
            self.position = 0
            while True:
                player = self.current_player
                print(colorize(f"\nХод игрока {self.names[player]}", 'cyan'))
                input("Нажмите Enter, чтобы выстрелить...")
                if self.delay:
                    time.sleep(self.delay)
                shot = self.fire(player)
                if shot:
                    print(colorize(f"💥 БАХ! {self.names[player]} проиграл!", 'red'))
                    # Обновляем статистику
                    self.update_stats(player, win=False)
                    # Остальные игроки считаются победителями
                    for i in range(self.players_count):
                        if i != player:
                            self.update_stats(i, win=True)
                    return
                else:
                    print(colorize("Щелчок...", 'yellow'))
                    self.current_player = (self.current_player + 1) % self.players_count
                    if self.all_players_shot():
                        # Все выжили, ничья
                        print(colorize("Все выжили! Ничья.", 'blue'))
                        for i in range(self.players_count):
                            self.update_stats(i, win=False)
                        return

        elif self.mode == 'spin':
            self.load_chamber()
            while True:
                player = self.current_player
                print(colorize(f"\nХод игрока {self.names[player]}", 'cyan'))
                self.spin()
                input("Нажмите Enter, чтобы выстрелить...")
                if self.delay:
                    time.sleep(self.delay)
                shot = self.fire(player)
                if shot:
                    print(colorize(f"💥 БАХ! {self.names[player]} проиграл!", 'red'))
                    self.update_stats(player, win=False)
                    for i in range(self.players_count):
                        if i != player:
                            self.update_stats(i, win=True)
                    return
                else:
                    print(colorize("Щелчок...", 'yellow'))
                    self.current_player = (self.current_player + 1) % self.players_count
                    if self.all_players_shot():
                        print(colorize("Все выжили! Ничья.", 'blue'))
                        for i in range(self.players_count):
                            self.update_stats(i, win=False)
                        return

        elif self.mode == 'duel':
            # Дуэль: игроки ходят по очереди, пока кто-то не выстрелит
            self.load_chamber()
            self.position = 0
            while True:
                player = self.current_player
                print(colorize(f"\nДуэль! Ход {self.names[player]}", 'cyan'))
                input("Нажмите Enter, чтобы выстрелить...")
                if self.delay:
                    time.sleep(self.delay)
                shot = self.fire(player)
                if shot:
                    print(colorize(f"💥 {self.names[player]} проиграл дуэль!", 'red'))
                    self.update_stats(player, win=False)
                    other = 1 - player
                    self.update_stats(other, win=True)
                    return
                else:
                    print(colorize("Щелчок...", 'yellow'))
                    self.current_player = (self.current_player + 1) % self.players_count

        elif self.mode == 'survival':
            # Один игрок против револьвера, можно остановиться в любой момент
            self.load_chamber()
            self.position = 0
            player = 0
            round_score = 0
            print(colorize(f"\nРежим выживания для {self.names[player]}", 'blue'))
            while True:
                print(colorize(f"Текущий счёт: {round_score}", 'yellow'))
                choice = input("Выстрелить? (y/n): ").strip().lower()
                if choice == 'q':
                    print("Выход.")
                    return
                if choice == 'n':
                    print(colorize(f"Вы остановились с {round_score} очками.", 'green'))
                    self.update_stats(player, win=True, extra=round_score)
                    return
                if choice == 'y':
                    if self.delay:
                        time.sleep(self.delay)
                    shot = self.fire(player)
                    if shot:
                        print(colorize(f"💥 БАХ! Вы проиграли! Очки: {round_score}", 'red'))
                        self.update_stats(player, win=False, extra=round_score)
                        return
                    else:
                        print(colorize("Щелчок...", 'yellow'))
                        round_score += 1
                        self.position = (self.position + 1) % self.chambers
                        # Каждый выстрел увеличивает шанс (можно менять количество патронов)
                        # Для простоты оставляем как есть

    def all_players_shot(self):
        # Проверяем, сделали ли все игроки по одному выстрелу без результата
        # В классическом режиме после каждого выстрела ход переходит, и если все выжили - ничья
        # Здесь считаем, что если каждый игрок выстрелил и никто не погиб, то ничья
        # Это упрощение; в реальной игре может продолжаться бесконечно.
        # Ограничим количество ходов
        if len(self.history) >= self.players_count:
            last_players = set()
            for i in range(len(self.history)-self.players_count, len(self.history)):
                last_players.add(self.history[i][0])
            if len(last_players) == self.players_count:
                return True
        return False

    def update_stats(self, player_idx, win, extra=0):
        name = self.names[player_idx]
        if name not in self.stats:
            self.stats[name] = {'games': 0, 'wins': 0, 'losses': 0, 'score': 0}
        self.stats[name]['games'] += 1
        if win:
            self.stats[name]['wins'] += 1
        else:
            self.stats[name]['losses'] += 1
        self.stats[name]['score'] += extra
        self.save_stats()

    def play(self):
        print(colorize("🔫 Добро пожаловать в русскую рулетку!", 'bold'))
        print(f"Режим: {self.mode}, Игроков: {self.players_count}, Патронов: {self.bullets}, Гнёзд: {self.chambers}")
        if self.delay:
            print(f"Задержка: {self.delay*1000} мс")
        print("Имена игроков: " + ", ".join(self.names))
        print()
        self.play_round()

def main():
    parser = argparse.ArgumentParser(description="Russian Roulette Simulation")
    parser.add_argument('mode', choices=['classic', 'spin', 'duel', 'survival'],
                        help='Режим игры')
    parser.add_argument('-p', '--players', type=int, default=2, help='Количество игроков (2-6)')
    parser.add_argument('-b', '--bullets', type=int, default=1, help='Количество патронов')
    parser.add_argument('-c', '--chambers', type=int, default=6, help='Количество гнёзд в барабане')
    parser.add_argument('-n', '--names', help='Имена игроков через запятую')
    parser.add_argument('-d', '--delay', type=float, default=0.2, help='Задержка между действиями (сек)')
    parser.add_argument('-s', '--stats', action='store_true', help='Показать статистику')
    args = parser.parse_args()

    if args.stats:
        game = Roulette(args.mode, args.players, args.bullets, args.chambers, delay=args.delay)
        game.display_stats()
        return

    names = None
    if args.names:
        names = [name.strip() for name in args.names.split(',')]
        if len(names) != args.players:
            print(colorize("Количество имён должно совпадать с числом игроков.", 'red'))
            sys.exit(1)
    game = Roulette(args.mode, args.players, args.bullets, args.chambers, names, args.delay)
    game.play()

if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print(colorize("\nИгра прервана.", 'yellow'))
        sys.exit(0)
