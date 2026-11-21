# DripleafBonemealFix 1.0.0 源码

构建：在 plugin 目录执行  .\build.ps1 -Clean -Deploy
      产物 build\dist\DripleafBonemealFix-1.0.0.jar

要求：
  - JDK 21+（脚本优先用 %JAVA_HOME%，回退 D:\pcl\java23）
  - 编译 classpath 需要同版本 Paper/Leaf 服务端的 libraries 目录
    （可用环境变量 PAPER_SERVER_DIR 指向服务器目录，或把 leaf-api jar 放进 lib\）

源码结构：
  src\main\java\com\ghosttune\dripleafbonemealfix\DripleafBonemealFix.java   插件主类
  src\main\java\com\ghosttune\dripleafbonemealfix\DripleafListener.java      事件监听与生长算法
  src\main\resources\plugin.yml
  src\main\resources\config.yml
