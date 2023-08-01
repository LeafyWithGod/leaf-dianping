-- 参数列表
-- 优惠券id
local voucherId = ARGV[1]
print("voucherId " .. voucherId)
voucherId=string.gsub(voucherId,'"','')
-- 用户id
local userId = ARGV[2]
userId=string.gsub(userId,'""','')
-- 数据keu
-- 库存key
local stockKey =  "seckill:stock:".. voucherId
print("voucherId " .. voucherId)
-- 订单key
local orderKey = "seckill:order:" .. voucherId

-- 脚本业务
-- 判断库存充足
if(tonumber(redis.call('get',stockKey)) <= 0) then
    --库存不足返回1
    return 1
end


--判断是否下单过 SISMEMBER orderKey userId 判断userId是否在orderKey当中
if(tonumber(redis.call('sismember',orderKey,userId))==1) then
    -- 存在说明是重复下单
    return 2
end
-- 代码走到这里说明库存充足+没有下过单
-- 扣库存
redis.call('incrby',stockKey,-1)
-- 下单
redis.call('sadd',orderKey,userId)
return 0