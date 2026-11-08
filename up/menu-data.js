/* Moli Taste menu — edit names / prices here; both pages update automatically.
   from:true  → shows "起" (starting price, size options).                      */
window.MENU = [
  { id: "pizza", zh: "披萨", en: "Pizza", ar: "بيتزا", tr: "Pizza", items: [
    { zh: "奶酪扎塔", en: "Zatar with Cheese", ar: "زعتر بالجبنة", tr: "Peynirli Zahter", price: 68, img: "pizza-zatar-cheese" },
    { zh: "扎塔", en: "Zatar", ar: "زعتر", tr: "Zahter", price: 58, img: "pizza-zatar" },
    { zh: "凯撒芝士风味炸鸡", en: "Caesar Fried Chicken", ar: "دجاج مقلي سيزر", tr: "Sezar Çıtır Tavuk", price: 58, img: "pizza-caesar" },
    { zh: "四重奶酪", en: "Four Cheese", ar: "أربعة أجبان", tr: "Dört Peynirli", price: 75, img: "pizza-four-cheese" },
    { zh: "烟熏三文鱼", en: "Smoked Salmon", ar: "سلمون مدخن", tr: "Füme Somon", price: 82, img: "pizza-smoked-salmon" },
    { zh: "鲜烤大虾", en: "Shrimp", ar: "روبيان", tr: "Karides", price: 78, from: true, img: "pizza-shrimp" },
    { zh: "鲜烤牛肉", en: "Grilled Beef", ar: "لحم بقري مشوي", tr: "Izgara Dana", price: 75, img: "pizza-grilled-beef" },
    { zh: "鲜烤鸡肉", en: "Grilled Chicken", ar: "دجاج مشوي", tr: "Izgara Tavuk", price: 68, from: true, img: "pizza-grilled-chicken" },
    { zh: "混合蔬菜", en: "Vege Mix", ar: "خضار مشكلة", tr: "Karışık Sebze", price: 68, img: "pizza-vege" },
    { zh: "意式香肠", en: "Pepperoni", ar: "ببروني", tr: "Pepperoni", price: 75, img: "pizza-pepperoni" },
    { zh: "玛格丽特", en: "Margherita", ar: "مارغريتا", tr: "Margherita", price: 68, img: "pizza-margherita" },
  ]},
  { id: "specials", zh: "茉莉特色餐", en: "Moli Specials", ar: "أطباق مولي الخاصة", tr: "Moli Spesyalleri", items: [
    { zh: "茉莉巨无霸盒", en: "Moli Big Box", ar: "صندوق مولي الكبير", tr: "Moli Büyük Kutu", price: 48, img: "special-moli-box" },
    { zh: "芝士白酱意面", en: "Béchamel Cheese Pasta", ar: "معكرونة بالبشاميل والجبنة", tr: "Beşamelli Peynirli Makarna", price: 58, img: "special-bechamel" },
  ]},
  { id: "burger", zh: "汉堡", en: "Burgers", ar: "برغر", tr: "Burgerler", items: [
    { zh: "鸡肉汉堡", en: "Fried Chicken Burger", ar: "برغر دجاج مقلي", tr: "Çıtır Tavuk Burger", price: 38, from: true, img: "burger-chicken" },
    { zh: "牛肉汉堡", en: "Beef Burger", ar: "برغر لحم", tr: "Dana Burger", price: 48, from: true, img: "burger-beef" },
  ]},
  { id: "sandwich", zh: "三明治", en: "Sandwiches", ar: "سندويشات", tr: "Sandviçler", items: [
    { zh: "茉莉潜艇·鲜烤鸡肉", en: "Moli Sub · Grilled Chicken", ar: "مولي ساب · دجاج مشوي", tr: "Moli Sub · Izgara Tavuk", price: 37, img: "sub-grilled-chicken" },
    { zh: "茉莉潜艇·芝士蔬菜", en: "Moli Sub · Cheese & Vegetables", ar: "مولي ساب · جبنة وخضار", tr: "Moli Sub · Peynir ve Sebze", price: 38, img: "sub-cheese" },
    { zh: "茉莉潜艇·鲜烤牛肉", en: "Moli Sub · Grilled Beef", ar: "مولي ساب · لحم مشوي", tr: "Moli Sub · Izgara Dana", price: 42, img: "sub-grilled-beef" },
    { zh: "茉莉潜艇·凯撒芝士风味炸鸡", en: "Moli Sub · Caesar Fried Chicken", ar: "مولي ساب · دجاج مقلي سيزر", tr: "Moli Sub · Sezar Çıtır Tavuk", price: 35, img: "sub-caesar" },
    { zh: "那不勒斯·芝士蔬菜", en: "Napolitano · Cheese & Vegetables", ar: "نابوليتانو · جبنة وخضار", tr: "Napolitano · Peynir ve Sebze", price: 48, img: "napo-cheese-veg" },
    { zh: "那不勒斯·鲜烤鸡肉", en: "Napolitano · Grilled Chicken", ar: "نابوليتانو · دجاج مشوي", tr: "Napolitano · Izgara Tavuk", price: 48, img: "napo-grilled-chicken" },
    { zh: "那不勒斯·鲜烤牛肉", en: "Napolitano · Grilled Beef", ar: "نابوليتانو · لحم مشوي", tr: "Napolitano · Izgara Dana", price: 51, img: "napo-grilled-beef" },
    { zh: "那不勒斯·意式香肠马苏里拉", en: "Napolitano · Pepperoni Mozzarella", ar: "نابوليتانو · ببروني وموزاريلا", tr: "Napolitano · Pepperoni Mozzarella", price: 53, img: "napo-pepperoni" },
    { zh: "那不勒斯·凯撒芝士风味炸鸡", en: "Napolitano · Caesar Fried Chicken", ar: "نابوليتانو · دجاج مقلي سيزر", tr: "Napolitano · Sezar Çıtır Tavuk", price: 45, img: "napo-caesar" },
  ]},
  { id: "coffee", zh: "咖啡", en: "Coffee", ar: "قهوة", tr: "Kahve", items: [
    { zh: "卡布奇诺", en: "Cappuccino", ar: "كابتشينو", tr: "Cappuccino", price: 30, img: "coffee-cappuccino" },
    { zh: "拿铁", en: "Latte", ar: "لاتيه", tr: "Latte", price: 28, img: "coffee-latte" },
    { zh: "美式", en: "Americano", ar: "أمريكانو", tr: "Americano", price: 15, from: true, img: "coffee-americano" },
    { zh: "浓缩咖啡", en: "Espresso", ar: "إسبريسو", tr: "Espresso", price: 18, from: true, img: "coffee-espresso" },
  ]},
  { id: "drinks", zh: "饮品", en: "Drinks", ar: "مشروبات", tr: "İçecekler", items: [
    { zh: "冰草莓果汁", en: "Iced Strawberry Juice", ar: "عصير فراولة مثلج", tr: "Buzlu Çilek Suyu", price: 22, img: "drink-strawberry" },
  ]},
  { id: "gelato", zh: "意式冰淇淋", en: "Gelato", ar: "جيلاتو", tr: "Gelato", items: [
    { zh: "芒果", en: "Mango", ar: "مانجو", tr: "Mango", price: 16, img: "gelato-mango" },
    { zh: "巧克力", en: "Chocolate", ar: "شوكولاتة", tr: "Çikolata", price: 18, img: "gelato-chocolate" },
    { zh: "开心果", en: "Pistachio", ar: "فستق حلبي", tr: "Antep Fıstığı", price: 22, img: "gelato-pistachio" },
  ]},
  { id: "salad", zh: "沙拉", en: "Salads", ar: "سلطات", tr: "Salatalar", items: [
    { zh: "菲达奶酪沙拉", en: "Feta Green Salad", ar: "سلطة خضراء بالفيتا", tr: "Beyaz Peynirli Yeşil Salata", price: 28, from: true, img: "salad-feta" },
    { zh: "三文鱼沙拉", en: "Salmon Salad", ar: "سلطة سلمون", tr: "Somonlu Salata", price: 32, from: true, img: "salad-salmon" },
    { zh: "凯撒沙拉", en: "Caesar Salad", ar: "سلطة سيزر", tr: "Sezar Salata", price: 25, from: true, img: "salad-caesar" },
    { zh: "绿色沙拉", en: "Green Salad", ar: "سلطة خضراء", tr: "Yeşil Salata", price: 18, from: true, img: "salad-green" },
  ]},
  { id: "snacks", zh: "小吃", en: "Snacks", ar: "مقبلات خفيفة", tr: "Atıştırmalıklar", items: [
    { zh: "鸡柳", en: "Chicken Nuggets", ar: "أصابع دجاج", tr: "Çıtır Tavuk Parçaları", price: 20, img: "snack-nuggets" },
    { zh: "鹰嘴豆泥+面包", en: "Hummus with Bread", ar: "حمص مع خبز", tr: "Ekmekli Humus", price: 28, img: "snack-hummus-bread" },
    { zh: "鹰嘴豆泥", en: "Hummus", ar: "حمص", tr: "Humus", price: 20, img: "snack-hummus" },
    { zh: "薯条", en: "Fries", ar: "بطاطا مقلية", tr: "Patates Kızartması", price: 15, from: true, img: "snack-fries" },
  ]},
];
