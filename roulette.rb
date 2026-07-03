#!/usr/bin/env ruby
# roulette.rb
# encoding: UTF-8

require 'json'
require 'fileutils'
require 'timeout'

COLORS = {
  reset: "\e[0m",
  green: "\e[92m",
  red: "\e[91m",
  yellow: "\e[93m",
  blue: "\e[94m",
  cyan: "\e[96m",
  bold: "\e[1m"
}

def colorize(text, color)
  "#{COLORS[color]}#{text}#{COLORS[:reset]}"
end

class Roulette
  attr_reader :mode, :players_count, :bullets, :chambers, :delay, :names, :stats, :stats_file

  def initialize(mode, players, bullets, chambers, delay, names)
    @mode = mode
    @players_count = players
    @bullets = bullets
    @chambers = chambers
    @delay = delay
    @names = names || (1..players).map { |i| "Игрок #{i}" }
    @chamber = []
    @position = 0
    @current = 0
    @history = []
    @stats_file = File.join(Dir.home, '.roulette_stats.json')
    load_stats
  end

  def load_stats
    if File.exist?(@stats_file)
      @stats = JSON.parse(File.read(@stats_file))
    else
      @stats = {}
    end
  end

  def save_stats
    File.write(@stats_file, JSON.pretty_generate(@stats))
  end

  def display_stats
    if @stats.empty?
      puts colorize("Статистика пуста.", :yellow)
      return
    end
    @stats.each do |name, data|
      puts colorize("#{name}:", :bold)
      puts "  Игр: #{data['games']}, Побед: #{data['wins']}, Поражений: #{data['losses']}, Очки: #{data['score']}"
    end
  end

  def load_chamber
    @chamber = [0] * @chambers
    positions = (0...@chambers).to_a.shuffle
    @bullets.times { |i| @chamber[positions[i]] = 1 }
    @position = 0
  end

  def spin
    @position = rand(@chambers)
  end

  def fire(player)
    if @chamber[@position] == 1
      @history << { player: player, shot: true }
      return true
    else
      @history << { player: player, shot: false }
      @position = (@position + 1) % @chambers
      return false
    end
  end

  def update_stats(player, win, extra = 0)
    name = @names[player]
    @stats[name] ||= { 'games' => 0, 'wins' => 0, 'losses' => 0, 'score' => 0 }
    @stats[name]['games'] += 1
    if win
      @stats[name]['wins'] += 1
    else
      @stats[name]['losses'] += 1
    end
    @stats[name]['score'] += extra
    save_stats
  end

  def all_players_shot?
    return false if @history.size < @players_count
    last = @history.last(@players_count).map { |h| h[:player] }.uniq
    last.size == @players_count
  end

  def play_round
    if @mode == 'classic'
      load_chamber
      @position = 0
      loop do
        player = @current
        puts colorize("\nХод игрока #{@names[player]}", :cyan)
        print "Нажмите Enter, чтобы выстрелить..."
        STDIN.gets
        sleep @delay if @delay > 0
        shot = fire(player)
        if shot
          puts colorize("💥 БАХ! #{@names[player]} проиграл!", :red)
          update_stats(player, false)
          @players_count.times { |i| update_stats(i, true) if i != player }
          return
        else
          puts colorize("Щелчок...", :yellow)
          @current = (@current + 1) % @players_count
          if all_players_shot?
            puts colorize("Все выжили! Ничья.", :blue)
            @players_count.times { |i| update_stats(i, false) }
            return
          end
        end
      end
    elsif @mode == 'spin'
      load_chamber
      loop do
        player = @current
        puts colorize("\nХод игрока #{@names[player]}", :cyan)
        spin
        print "Нажмите Enter, чтобы выстрелить..."
        STDIN.gets
        sleep @delay if @delay > 0
        shot = fire(player)
        if shot
          puts colorize("💥 БАХ! #{@names[player]} проиграл!", :red)
          update_stats(player, false)
          @players_count.times { |i| update_stats(i, true) if i != player }
          return
        else
          puts colorize("Щелчок...", :yellow)
          @current = (@current + 1) % @players_count
          if all_players_shot?
            puts colorize("Все выжили! Ничья.", :blue)
            @players_count.times { |i| update_stats(i, false) }
            return
          end
        end
      end
    elsif @mode == 'duel'
      load_chamber
      @position = 0
      loop do
        player = @current
        puts colorize("\nДуэль! Ход #{@names[player]}", :cyan)
        print "Нажмите Enter, чтобы выстрелить..."
        STDIN.gets
        sleep @delay if @delay > 0
        shot = fire(player)
        if shot
          puts colorize("💥 #{@names[player]} проиграл дуэль!", :red)
          update_stats(player, false)
          other = 1 - player
          update_stats(other, true)
          return
        else
          puts colorize("Щелчок...", :yellow)
          @current = (@current + 1) % @players_count
        end
      end
    elsif @mode == 'survival'
      load_chamber
      @position = 0
      player = 0
      score = 0
      puts colorize("\nРежим выживания для #{@names[player]}", :blue)
      loop do
        puts colorize("Текущий счёт: #{score}", :yellow)
        print "Выстрелить? (y/n): "
        choice = STDIN.gets.chomp.strip.downcase
        if choice == 'q'
          puts "Выход."
          return
        end
        if choice == 'n'
          puts colorize("Вы остановились с #{score} очками.", :green)
          update_stats(player, true, score)
          return
        end
        if choice == 'y'
          sleep @delay if @delay > 0
          shot = fire(player)
          if shot
            puts colorize("💥 БАХ! Вы проиграли! Очки: #{score}", :red)
            update_stats(player, false, score)
            return
          else
            puts colorize("Щелчок...", :yellow)
            score += 1
            @position = (@position + 1) % @chambers
          end
        end
      end
    end
  end

  def play
    puts colorize("🔫 Добро пожаловать в русскую рулетку!", :bold)
    puts "Режим: #{@mode}, Игроков: #{@players_count}, Патронов: #{@bullets}, Гнёзд: #{@chambers}"
    puts "Задержка: #{@delay * 1000} мс" if @delay > 0
    puts "Имена: #{@names.join(', ')}"
    puts
    play_round
  end
end

def main
  mode = 'classic'
  players = 2
  bullets = 1
  chambers = 6
  delay = 0.2
  names = nil
  show_stats = false

  i = 0
  while i < ARGV.size
    arg = ARGV[i]
    case arg
    when 'classic', 'spin', 'duel', 'survival'
      mode = arg
    when '-p'
      players = ARGV[i+1].to_i
      i += 1
    when '-b'
      bullets = ARGV[i+1].to_i
      i += 1
    when '-c'
      chambers = ARGV[i+1].to_i
      i += 1
    when '-n'
      names = ARGV[i+1].split(',')
      i += 1
    when '-d'
      delay = ARGV[i+1].to_f
      i += 1
    when '-s'
      show_stats = true
    when '-h'
      puts "Usage: ruby roulette.rb [mode] [-p players] [-b bullets] [-c chambers] [-n names] [-d delay] [-s]"
      return
    end
    i += 1
  end
  names ||= (1..players).map { |i| "Игрок #{i}" }
  game = Roulette.new(mode, players, bullets, chambers, delay, names)
  if show_stats
    game.display_stats
    return
  end
  game.play
end

main if __FILE__ == $0
