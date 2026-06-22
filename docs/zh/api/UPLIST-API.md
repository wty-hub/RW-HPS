# RW-HPS - UPLIST-API-V5

> 注:
> - 本章节为 `RW-HPS` 特殊章节
> - 请配合 `RW-HPS-Core` 源码查看

----------------------

# V5 的目标

轻量化 简便化用户的使用

# 使用

> RW-HPS Core 现已直接向官方 masterserver 发送请求，不再使用 V5-API 代理。

## 替换

### 对于 `ADD` 需要替换

`{RW-HPS.RW.VERSION}` 为 `游戏版本(例如 1.14)`  
`{RW-HPS.RW.VERSION.INT}` 为 `游戏版本代号(例如 151)`  
`{RW-HPS.RW.IS.VERSION}` 为 `是否为测试版(例如 false)`  
`{RW-HPS.RW.IS.PASSWD}` 为 `是否为有密码(例如 false)`  
`{RW-HPS.S.NAME}` 为 `服务器名称(例如 RW-HPS)`  
`{RW-HPS.S.PRIVATE.IP}` 为 `区域网IP(例如 192.168.0.100)`  
`{RW-HPS.S.PORT}` 为 `服务器端口(例如 5123)`  
`{RW-HPS.RW.MAP.NAME}` 为 `服务器地图名称(例如 Crossing Large (10p))`  
`{RW-HPS.PLAYER.SIZE}` 为 `服务器当前人数(例如 1)`  
`{RW-HPS.PLAYER.SIZE.MAX}`  为 `服务器最大人数(例如 10)`

### 对于 `UPDATE` 需要替换

`{RW-HPS.RW.IS.PASSWD}` 为 `是否为有密码(例如 false)`  
`{RW-HPS.S.NAME}` 为 `服务器名称(例如 RW-HPS)`  
`{RW-HPS.S.PRIVATE.IP}` 为 `区域网IP(例如 192.168.0.100)`  
`{RW-HPS.S.PORT}` 为 `服务器端口(例如 5123)`  
`{RW-HPS.RW.MAP.NAME}` 为 `服务器地图名称(例如 Crossing Large (10p))`  
`{RW-HPS.S.STATUS}` 为 `服务器状态(例如 ingame(游戏中) battleroom(战役室))`  
`{RW-HPS.PLAYER.SIZE}` 为 `服务器当前人数(例如 1)`  
`{RW-HPS.PLAYER.SIZE.MAX}`  为 `服务器最大人数(例如 10)`

### 对于 `open` 需要替换

`{RW-HPS.S.PORT}` 为 `服务器端口(例如 5123)`

### `remove` 不需要替换

## 使用

### 上列表

替换完成后 就可以向  
`http://gs1.corrodinggames.com/masterserver/1.4/interface`  
`http://gs4.corrodinggames.net/masterserver/1.4/interface`  
发送 POST 请求来上列表了

返回的数据应包含 ID (即上文 `API` 返回 `Json ID` 并解码后的数据)

### 端口开放

替换完成后 就可以向  
`http://gs1.corrodinggames.com/masterserver/1.4/interface`  
`http://gs4.corrodinggames.net/masterserver/1.4/interface`  
发送 POST 请求来上列表了

返回的数据应包含 `IP,true` 若返回为 `IP,false` 那么即端口不开放

### 更新

更新同理

### 删除

同理



