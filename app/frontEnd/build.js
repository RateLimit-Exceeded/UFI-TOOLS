const fs = require('fs');
const path = require('path');
const isDebug = process.argv.includes('--debug');
const inputDir = path.resolve(__dirname, 'public');
const outputDir = path.resolve(__dirname, '../src/main/assets/');

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

function copyFile(entryPath, outPath) {
    fs.copyFileSync(entryPath, outPath);
    const tag = entryPath.endsWith('.js') ? '🔄 Copied (js)' : '📄 Copied';
    console.log(`${tag}: ${entryPath} -> ${outPath}`);
}

// 递归处理目录
function processDirectory(dir, outDir) {
    const entries = fs.readdirSync(dir);

    entries.forEach((entry) => {
        const entryPath = path.join(dir, entry);
        const outPath = path.join(outDir, entry);
        const stat = fs.statSync(entryPath);

        if (stat.isDirectory()) {
            fs.mkdirSync(outPath, { recursive: true });
            processDirectory(entryPath, outPath);
        } else if (stat.isFile()) {
            copyFile(entryPath, outPath);
        }
    });
}

console.log('[INFO] JS 混淆已彻底禁用，所有文件将保持原样。');

processDirectory(inputDir, outputDir);
console.log('\n✅ 所有文件处理完毕！');
