# RW-HPS - UPLIST-API-V5

> Note:
> - This section is a special section of `RW-HPS`.
> - Please refer to the `RW-HPS-Core` source code for details.

----------------------

# Goals of V5

Lightweight and easy-to-use for users.

# Usage

> RW-HPS Core now sends requests directly to the official masterserver and no longer uses the V5-API proxy.

## Replacement

### For `ADD`, Replace with:

`{RW-HPS.RW.VERSION}` as `Game Version (e.g., 1.14)`  
`{RW-HPS.RW.VERSION.INT}` as `Game Version Code (e.g., 151)`  
`{RW-HPS.RW.IS.VERSION}` as `Is Test Version (e.g., false)`  
`{RW-HPS.RW.IS.PASSWD}` as `Has Password (e.g., false)`  
`{RW-HPS.S.NAME}` as `Server Name (e.g., RW-HPS)`  
`{RW-HPS.S.PRIVATE.IP}` as `Local IP (e.g., 192.168.0.100)`  
`{RW-HPS.S.PORT}` as `Server Port (e.g., 5123)`  
`{RW-HPS.RW.MAP.NAME}` as `Server Map Name (e.g., Crossing Large (10p))`  
`{RW-HPS.PLAYER.SIZE}` as `Current Player Count (e.g., 1)`  
`{RW-HPS.PLAYER.SIZE.MAX}`  as `Maximum Player Count (e.g., 10)`

### For `UPDATE`, Replace with:

`{RW-HPS.RW.IS.PASSWD}` as `Has Password (e.g., false)`  
`{RW-HPS.S.NAME}` as `Server Name (e.g., RW-HPS)`  
`{RW-HPS.S.PRIVATE.IP}` as `Local IP (e.g., 192.168.0.100)`  
`{RW-HPS.S.PORT}` as `Server Port (e.g., 5123)`  
`{RW-HPS.RW.MAP.NAME}` as `Server Map Name (e.g., Crossing Large (10p))`  
`{RW-HPS.S.STATUS}` as `Server Status (e.g., ingame (In-game) battleroom (Battle Room))`  
`{RW-HPS.PLAYER.SIZE}` as `Current Player Count (e.g., 1)`  
`{RW-HPS.PLAYER.SIZE.MAX}`  as `Maximum Player Count (e.g., 10)`

### For `open`, Replace with:

`{RW-HPS.S.PORT}` as `Server Port (e.g., 5123)`

### No Replacement Needed for `remove`.

## Usage

### Up-listing

After replacement, you can send a POST request to:  
`http://gs1.corrodinggames.com/masterserver/1.4/interface`  
`http://gs4.corrodinggames.net/masterserver/1.4/interface`  

The returned data should contain an ID (decoded from the JSON ID returned by the API).

### Port Opening

After replacement, you can send a POST request to:  
`http://gs1.corrodinggames.com/masterserver/1.4/interface`  
`http://gs4.corrodinggames.net/masterserver/1.4/interface`  

The returned data should contain `IP,true`. If it returns `IP,false`, the port is not open.

### Update

Same process as above.

### Delete

Same process as above.