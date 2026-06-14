$ErrorActionPreference = "Stop"

$env:SPINECARE_DEP_ROOT = "D:\2026\codexwork\SpinecareMom"
$env:NODE_PATH = "D:\2026\codexwork\SpinecareMom\node_modules;C:\Users\zclei\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules"

$node = "C:\Users\zclei\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$server = "D:\2026\202606\SpinecareMom\scripts\server.js"

& $node $server
