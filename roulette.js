// roulette.js
#!/usr/bin/env node
'use strict';

const readline = require('readline');
const fs = require('fs');
const path = require('path');
const os = require('os');

const COLORS = {
    reset: '\x1b[0m',
    green: '\x1b[92m',
    red: '\x1b[91m',
    yellow: '\x1b[93m',
    blue: '\x1b[94m',
    cyan: '\x1b[96m',
    bold: '\x1b[1m'
};

function colorize(text, color) {
    return COLORS[color] + text + COLORS.reset;
}

class Roulette {
    constructor(mode, players, bullets, chambers, delay, names) {
        this.mode = mode;
        this.playersCount = players;
        this.bullets = bullets;
        this.chambers = chambers;
        this.delay = delay;
        this.names = names || Array.from({length: players}, (_,i) => `Игрок ${i+1}`);
        this.chamber = [];
        this.position = 0;
        this.current = 0;
        this.history = [];
        this.statsFile = path.join(os.homedir(), '.roulette_stats.json');
        this.stats = this.loadStats();
    }

    loadStats() {
        try {
            return JSON.parse(fs.readFileSync(this.statsFile, 'utf8'));
        } catch {
            return {};
        }
    }

    saveStats() {
        fs.writeFileSync(this.statsFile, JSON.stringify(this.stats, null, 2));
    }

    displayStats() {
        if (Object.keys(this.stats).length === 0) {
            console.log(colorize('Статистика пуста.', 'yellow'));
            return;
        }
        for (const [name, data] of Object.entries(this.stats)) {
            console.log(colorize(`${name}:`, 'bold'));
            console.log(`  Игр: ${data.games}, Побед: ${data.wins}, Поражений: ${data.losses}, Очки: ${data.score}`);
        }
    }

    loadChamber() {
        this.chamber = new Array(this.chambers).fill(0);
        const positions = Array.from({length: this.chambers}, (_,i) => i);
        for (let i = positions.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [positions[i], positions[j]] = [positions[j], positions[i]];
        }
        for (let i = 0; i < this.bullets; i++) {
            this.chamber[positions[i]] = 1;
        }
        this.position = 0;
    }

    spin() {
        this.position = Math.floor(Math.random() * this.chambers);
    }

    fire(player) {
        if (this.chamber[this.position] === 1) {
            this.history.push({player, shot: true});
            return true;
        } else {
            this.history.push({player, shot: false});
            this.position = (this.position + 1) % this.chambers;
            return false;
        }
    }

    updateStats(player, win, extra = 0) {
        const name = this.names[player];
        if (!this.stats[name]) {
            this.stats[name] = { games: 0, wins: 0, losses: 0, score: 0 };
        }
        this.stats[name].games++;
        if (win) this.stats[name].wins++;
        else this.stats[name].losses++;
        this.stats[name].score += extra;
        this.saveStats();
    }

    allPlayersShot() {
        if (this.history.length < this.playersCount) return false;
        const last = new Set();
        for (let i = this.history.length - this.playersCount; i < this.history.length; i++) {
            last.add(this.history[i].player);
        }
        return last.size === this.playersCount;
    }

    async playRound() {
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
        const question = (q) => new Promise(resolve => rl.question(q, resolve));

        if (this.mode === 'classic') {
            this.loadChamber();
            this.position = 0;
            while (true) {
                const player = this.current;
                console.log(colorize(`\nХод игрока ${this.names[player]}`, 'cyan'));
                await question('Нажмите Enter, чтобы выстрелить...');
                if (this.delay > 0) await new Promise(r => setTimeout(r, this.delay * 1000));
                const shot = this.fire(player);
                if (shot) {
                    console.log(colorize(`💥 БАХ! ${this.names[player]} проиграл!`, 'red'));
                    this.updateStats(player, false);
                    for (let i = 0; i < this.playersCount; i++) {
                        if (i !== player) this.updateStats(i, true);
                    }
                    rl.close();
                    return;
                } else {
                    console.log(colorize('Щелчок...', 'yellow'));
                    this.current = (this.current + 1) % this.playersCount;
                    if (this.allPlayersShot()) {
                        console.log(colorize('Все выжили! Ничья.', 'blue'));
                        for (let i = 0; i < this.playersCount; i++) {
                            this.updateStats(i, false);
                        }
                        rl.close();
                        return;
                    }
                }
            }
        } else if (this.mode === 'spin') {
            this.loadChamber();
            while (true) {
                const player = this.current;
                console.log(colorize(`\nХод игрока ${this.names[player]}`, 'cyan'));
                this.spin();
                await question('Нажмите Enter, чтобы выстрелить...');
                if (this.delay > 0) await new Promise(r => setTimeout(r, this.delay * 1000));
                const shot = this.fire(player);
                if (shot) {
                    console.log(colorize(`💥 БАХ! ${this.names[player]} проиграл!`, 'red'));
                    this.updateStats(player, false);
                    for (let i = 0; i < this.playersCount; i++) {
                        if (i !== player) this.updateStats(i, true);
                    }
                    rl.close();
                    return;
                } else {
                    console.log(colorize('Щелчок...', 'yellow'));
                    this.current = (this.current + 1) % this.playersCount;
                    if (this.allPlayersShot()) {
                        console.log(colorize('Все выжили! Ничья.', 'blue'));
                        for (let i = 0; i < this.playersCount; i++) {
                            this.updateStats(i, false);
                        }
                        rl.close();
                        return;
                    }
                }
            }
        } else if (this.mode === 'duel') {
            this.loadChamber();
            this.position = 0;
            while (true) {
                const player = this.current;
                console.log(colorize(`\nДуэль! Ход ${this.names[player]}`, 'cyan'));
                await question('Нажмите Enter, чтобы выстрелить...');
                if (this.delay > 0) await new Promise(r => setTimeout(r, this.delay * 1000));
                const shot = this.fire(player);
                if (shot) {
                    console.log(colorize(`💥 ${this.names[player]} проиграл дуэль!`, 'red'));
                    this.updateStats(player, false);
                    const other = 1 - player;
                    this.updateStats(other, true);
                    rl.close();
                    return;
                } else {
                    console.log(colorize('Щелчок...', 'yellow'));
                    this.current = (this.current + 1) % this.playersCount;
                }
            }
        } else if (this.mode === 'survival') {
            this.loadChamber();
            this.position = 0;
            const player = 0;
            let score = 0;
            console.log(colorize(`\nРежим выживания для ${this.names[player]}`, 'blue'));
            while (true) {
                console.log(colorize(`Текущий счёт: ${score}`, 'yellow'));
                const choice = await question('Выстрелить? (y/n): ');
                if (choice === 'q') {
                    console.log('Выход.');
                    rl.close();
                    return;
                }
                if (choice === 'n') {
                    console.log(colorize(`Вы остановились с ${score} очками.`, 'green'));
                    this.updateStats(player, true, score);
                    rl.close();
                    return;
                }
                if (choice === 'y') {
                    if (this.delay > 0) await new Promise(r => setTimeout(r, this.delay * 1000));
                    const shot = this.fire(player);
                    if (shot) {
                        console.log(colorize(`💥 БАХ! Вы проиграли! Очки: ${score}`, 'red'));
                        this.updateStats(player, false, score);
                        rl.close();
                        return;
                    } else {
                        console.log(colorize('Щелчок...', 'yellow'));
                        score++;
                        this.position = (this.position + 1) % this.chambers;
                    }
                }
            }
        }
        rl.close();
    }

    async play() {
        console.log(colorize('🔫 Добро пожаловать в русскую рулетку!', 'bold'));
        console.log(`Режим: ${this.mode}, Игроков: ${this.playersCount}, Патронов: ${this.bullets}, Гнёзд: ${this.chambers}`);
        if (this.delay > 0) console.log(`Задержка: ${this.delay*1000} мс`);
        console.log('Имена: ' + this.names.join(', '));
        console.log();
        await this.playRound();
    }
}

async function main() {
    const args = process.argv.slice(2);
    let mode = 'classic';
    let players = 2;
    let bullets = 1;
    let chambers = 6;
    let delay = 0.2;
    let names = null;
    let showStats = false;

    for (let i = 0; i < args.length; i++) {
        const arg = args[i];
        if (['classic', 'spin', 'duel', 'survival'].includes(arg)) {
            mode = arg;
        } else if (arg === '-p' && i+1 < args.length) {
            players = parseInt(args[++i]);
        } else if (arg === '-b' && i+1 < args.length) {
            bullets = parseInt(args[++i]);
        } else if (arg === '-c' && i+1 < args.length) {
            chambers = parseInt(args[++i]);
        } else if (arg === '-n' && i+1 < args.length) {
            names = args[++i].split(',');
        } else if (arg === '-d' && i+1 < args.length) {
            delay = parseFloat(args[++i]);
        } else if (arg === '-s') {
            showStats = true;
        } else if (arg === '-h') {
            console.log('Usage: node roulette.js [mode] [-p players] [-b bullets] [-c chambers] [-n names] [-d delay] [-s]');
            process.exit(0);
        }
    }
    if (!names) {
        names = Array.from({length: players}, (_,i) => `Игрок ${i+1}`);
    }
    const game = new Roulette(mode, players, bullets, chambers, delay, names);
    if (showStats) {
        game.displayStats();
        return;
    }
    await game.play();
}

main().catch(console.error);
