<p align="center">
  <!--suppress HtmlRequiredAltAttribute -->
<img width="300" src="docs/img/airpower-bg.svg"/> <b>4J</b>
</p>

<p align="center">
  <img src="https://svg.hamm.cn?key=Lang&value=Java17&bg=green"/>
  <img src="https://svg.hamm.cn?key=Base&value=SpringBoot3"/>
  <img src="https://svg.hamm.cn?key=ORM&value=JPA"/>
  <img src="https://svg.hamm.cn?key=DB&value=MySQL"/>
  <img src="https://img.shields.io/maven-metadata/v.svg?label=Maven%20Central&metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcn%2Fhamm%2Fairpower%2Fmaven-metadata.xml"/>
</p>

<p align="center">
<a href="https://github.com/AirPowerTeam/AirPower4J">GitHub</a> / <a href="https://gitee.com/air-power/AirPower4J">Gitee</a> / <a href="./CHANGELOG.md">CHANGELOG</a> / <a href="https://central.sonatype.com/namespace/cn.hamm">Maven</a>
</p>

## 📖 一、这是个什么项目

为了满足开发标准化、工程化、系统化等等需求，我们设计并开发了一个基于 **Java17**、**SpringBoot3.x**、**JPA&MySQL**
的后端开发脚手架，其中包含了一些 **RBAC**、**请求验证**、**CURD封装**、**异常处理**、**多租户SaaS**、**加解密与安全**、
**WebSocket**等模块，以满足日常开发的快捷、稳健、标准化等要求。

当然，

> 如果你对全栈感兴趣，也可以和我们的 **`AirPower4T`**
> （[Github](https://github.com/AirPowerTeam/AirPower4T)/[Gitee](https://gitee.com/air-power/AirPower4T)） 一起玩耍，
`AirPower4T`
> 是一个基于 `Vue3`/`TypeScript`/`ElementPlus`/`Vite` 等技术栈的一个基础开发脚手架，`AirPower4J`和`AirPower4T`
> 的联合可以为你的全栈之路增加另外一份很不一样的开发体验。

## 🔑 二、如何使用(初始化)?

你可以使用 `maven` 来引入这个依赖包：

```xml
<!-- 指定你的项目的父级 pom 文件 -->
<parent>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower</artifactId>
    <version>${VERSION}</version>
</parent>
<dependencies>
<dependency>
    <groupId>cn.hamm</groupId>
    <artifactId>airpower-core</artifactId>
</dependency>
</dependencies>
```

## 💐 三、项目架构

### 1. 环境变量说明

我们使用了 `JPA` 的自动初始化数据库 `ddl-auto: create-drop` 模式，所以你在此项目中看不到SQL文件。

所以在初始化代码库完成后只需要先创建数据库，并设置 `utf8mb4_unicode_ci` 字符集。

接下来在环境变量中配置 `ddl-auto: create-drop` 即可。

> 请注意，生产环境请勿使用这种方式。

### 2. 基本架构说明

我们使用标准的 `Controller`/`Service`/`Repository` 架构，原则上不涉及 `EO`/`VO`/`DTO` 等，整个项目使用 `Entity` 作为数据结构。

> 一些比较特殊的需求除外。

### 3. 注解

我们提供了一系列的注解：

#### 3.1 `@Api`

标记为 API 控制器方法，等同于 `@RequestMapping` + `@RestController` 的整合。

#### 3.2 `@Description`

类或属性的文案，将显示在错误信息、验证信息等处。

#### 3.3 `@Desensitize` 与 `@DesensitizeIgnore`

标记脱敏字段和不脱敏的接口。

#### 3.4 `@ExcelColumn`

标记为Excel导出列，可配置导出列的数据类型。

#### 3.5 `@ReadOnly`

可为属性标记 `@ReadOnly` 表示该属性不参与控制器修改。

#### 3.6 `@Extends`

标记控制器需要从父类控制器中继承或排除哪些方法。

#### 3.7 `@Search`

标记属性参与搜索，可配置为模糊匹配、精确匹配、相等。

#### 3.8 `@Dictionary`

标记为字典属性，可使用下方 `4` 中的枚举字典接口的实现类。

### 4. 枚举字典

枚举字典需要实现 `IDictionary` 接口，即可使用 `3.9` 中的注解对属性进行标记，会自动进行判断和翻译。

### 5. `RootModel` 超类模型

所有参与API数据交互的部分都需要继承 `RootModel`, 一切需要入库的数据都需要继承 `RootEntity`。

### 6. `API` 控制器超类 和 `CURD` 控制器

所有控制器均需要继承 `ApiController`，其中，如果是数据库相关的控制器，需要继承 `CurdController`。

同时，如果是 `Curd` 相关业务的业务，可以继承和实现 `CurdService` 和 `ICurdRepository`

### 7. 自定义异常

自定义异常需要实现 `IException` 接口，即可使用异常的快捷抛出等方法。

### 8. 标准树

实现了 `ITree` 的类都可实现标准的树结构，可使用 `TreeUtil` 的一系列方法。

### 9. 系统配置

所有的服务配置都可通过环境变量注入，只需要在配置文件 `application-xxx.yml` 中根配置 `airpower.` 就可以全看到啦。

### 9. `Utils`

提供了大量的工具包以供使用，可以查看 `cn.hamm.airpower.util` 包下的类，也可以直接使用 `Utils.getXXX()` 直接获取工具类使用。

## 🛎 四、问题反馈与建议

如果你有什么疑问或者问题，你也可以加入开发者交流QQ群(```555156313```)
进行咨询，当然，我们更建议你发起 [Github issue](https://github.com/AirPowerTeam/AirPower4J/issues/new) / [Gitee issue](https://gitee.com/air-power/AirPower4J/issues/new)

## ⏰ 五、Enjoy it

好了, 那么接下来你可以愉快地开发了, 如果你有什么建议或者意见, 可以在本仓库中提交你的 **issues**, 你可以为这个依赖库进行
**添砖加瓦**!

> ☕️Java: 加瓦? 什么Java?

## 🎱 六、服务中的企业/用户

**AirPower4J** 正在为以下的公司/用户提供技术支持:

- **杭州某财税网络科技有限公司**

- **重庆某工业互联网科技有限公司**

> 如果你的公司/企业正在使用我们的服务，欢迎通过 `Issues` 提交，我们将在上面的列表中列出。

---

<p align="center">
ATTENTION: Contributor list is just for fun!!!
</p>
