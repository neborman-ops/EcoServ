-- EcoServ: магазин на компьютере с несколькими торговыми автоматами.
-- Подключи автоматы проводными модемами (или по бокам), монитор — по желанию.
-- Запуск: shop            (покупатель выбирает товар и подтверждает оплату в чате)
-- Владелец может один раз ввести ключ: shop <секрет>  (тот же, что /ecocc key <секрет>)

local args = { ... }
local machines = { peripheral.find("ecoserv_vending") }
if #machines == 0 then error("Нет подключённых ecoserv_vending") end

if args[1] then
  for _, m in ipairs(machines) do m.auth(args[1]) end
end

local function offers()
  local list = {}
  for _, m in ipairs(machines) do
    local o = m.getOffer()
    if o.configured then
      table.insert(list, { m = m, name = peripheral.getName(m), offer = o })
    end
  end
  return list
end

local function show(list)
  term.clear(); term.setCursorPos(1, 1)
  print("=== Магазин " .. machines[1].getOwner() .. " ===")
  for i, e in ipairs(list) do
    local o = e.offer
    local stock = o.stock < 0 and "∞" or tostring(o.purchases)
    print(("%d) %s x%d — %d монет (осталось %s)"):format(i, o.item.displayName or o.item.name, o.amount, o.price, stock))
  end
end

while true do
  local list = offers()
  show(list)
  write("Ваш ник: ")
  local nick = read()
  write("Номер товара: ")
  local n = tonumber(read())
  write("Сколько раз купить (1-64): ")
  local times = tonumber(read()) or 1
  local e = list[n or 0]
  if not e then
    print("Нет такого товара"); sleep(2)
  elseif not e.m.isNearby(nick) then
    print("Подойдите ближе к автомату"); sleep(2)
  else
    local ok, id = pcall(e.m.requestBuy, nick, times)
    if not ok then
      print("Ошибка: " .. tostring(id)); sleep(2)
    else
      print("Подтвердите покупку в чате...")
      while true do
        local ev = table.pack(os.pullEvent("ecoserv_purchase"))
        local rid, status, from = ev[2], ev[3], ev[ev.n]   -- имя периферии всегда последним
        if rid == id and from == e.name then
          if status == "ok" then
            print(("Готово: %d шт. за %d монет. Спасибо, %s!"):format(ev[6], ev[7], ev[4]))
          else
            print("Покупка не прошла: " .. tostring(status))
          end
          break
        end
      end
      sleep(3)
    end
  end
end
