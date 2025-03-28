# Readme
Cloudbeaver社区版是一个开源的数据库连接工具，但是不支持国内的数据，为进行适配采用自编译的方式通过Cloubeaver-ce wiki中的文档添加驱动。
在此文档中，cloudbeaver目录下Dockerfile.base 与 Dockerfile.build 两个docker镜像。这两个镜像是为了编译源码所准备的docker镜像环境。
因此你在添加了新的插件后，可以此在根目录下重新构建Dockerfile.build，并启动镜像进行编译源码。
最后将编译后的文件通过 cloudbeaver/deploy/make-container.sh 脚本进行打包成新的可运行镜像。
以上描述和打包方式或许简陋，但是主要步骤已经叙述和包含。


另外，不建议在内网环境进行打包，其所依赖的外网资源太多，如果你的企业内网无法正常链接 `https://maven.cubrid.org/`、`https://maven.exasol.com/artifactory/exasol-releases`等多个·仓库。
