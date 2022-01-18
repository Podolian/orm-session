Created a custom SessionFactory class that accepts DataSource and has a createSession method
Created a custom Session class that accepts DataSource and has methods find(Class<T> entityType, Object id) and close()
Created custom annotations Table, Column
Implemented method find using JDBC API
Introduced session cache
Store loaded entities to the map when it’s loaded
Don’t call the DB if entity with given id is already loaded, return value from the map instead
Introduced update mechanism
Created another map that stores an entity snapshot copy (initial field values)
On session close, compare entity with its copy and if at least one field has changed, perform UPDATE statement
