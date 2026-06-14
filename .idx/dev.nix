{ pkgs, ... }: {
  channel = "stable-23.11";
  packages = [
    pkgs.jdk17
    pkgs.android-tools
    pkgs.unzip
  ];
  env = {
    # 显式指定 JAVA 路径，防止环境找不到 JDK
    JAVA_HOME = "${pkgs.jdk17}";
  };
  idx = {
    extensions = [
      "muhammad-sammy.android-f-droid"
    ];
    previews = {
      enable = true;
      previews = {
        android = {
          # 尝试将 command 留空，让 manager 自动处理
          # 如果之前报错任务模糊，我们在下方配置默认任务
          manager = "android";
        };
      };
    };
  };
}