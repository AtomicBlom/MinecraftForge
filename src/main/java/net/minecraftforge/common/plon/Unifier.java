package net.minecraftforge.common.plon;

import com.google.common.base.*;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.*;
import net.minecraftforge.common.util.DisjointMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.Map;

import static net.minecraftforge.common.plon.AST.*;

/**
 * Created by rainwarrior on 5/31/16.
 */
public class Unifier
{
    //private final DisjointMap<ISExp> union = new DisjointMap<ISExp>(ops);
    // type vars are globally unique so this should be good
    // TODO: check
    //private final java.util.Map<Symbol, ISExp> boundVars = Maps.newHashMap();
    //private final Multimap<ISExp, ISExp> constraints = HashMultimap.create();
    private int nextVar = 0;
    ISExp getFreshVar()
    {
        ISExp symbol = makeSymbol("T" + nextVar++);
        globalEnv.substitution.makeSet((Symbol) symbol);
        return symbol;
    }

    private Environment globalEnv = new Environment();

    ISExp find(ISExp exp)
    {
        return globalEnv.find(exp);
    }

    void unify(ISExp first, ISExp second)
    {
        Set<Environment> ret = Sets.newHashSet();
        EnvSet envSet = unify(first, second, new EnvSet(ImmutableSet.of(globalEnv)));
        UnifyException exception = null;
        for (Environment env : envSet.evs)
        {
            try
            {
                env.checkLacks();
                ret.add(env);
            }
            catch (UnifyException e)
            {
                exception = e;
            }
        }
        if(ret.size() == 1)
        {
            globalEnv = ret.iterator().next();
        }
        else if(ret.size() == 0)
        {
            if(exception == null)
            {
                exception = envSet.exception;
            }
            throw new IllegalStateException("Couldn't unify: " + typeToString(first) + " and " + typeToString(second), exception);
        }
        else
        {
            throw new IllegalStateException("Couldn't unify uniquely: " + typeToString(first) + " and " + typeToString(second));
        }
    }

    void addLacks(ISExp r, ISExp label)
    {
        Multimap<ISExp, ISExp> lacks = HashMultimap.create(globalEnv.lacks);
        lacks.put(r, label);
        globalEnv = new Environment(globalEnv.substitution, ImmutableMultimap.copyOf(lacks));
    }

    private static final class UnionException extends RuntimeException
    {
        private final ISExp first;
        private final ISExp second;

        private UnionException(ISExp first, ISExp second)
        {
            super("Couldn't unify " + first + " and " + second);
            this.first = first;
            this.second = second;
        }
    }

    private enum UniqueOptionalValueOps implements DisjointMap.IValueOps<Symbol, Optional<ISExp>>
    {
        INSTANCE;

        @Override
        public Optional<ISExp> makeValue(Symbol symbol)
        {
            return Optional.absent();
        }

        @Override
        public Optional<ISExp> unionValues(Optional<ISExp> v1, Optional<ISExp> v2)
        {
            if(v1.isPresent())
            {
                if(v2.isPresent())
                {
                    ISExp val1 = v1.get();
                    ISExp val2 = v2.get();
                    if(!val1.equals(val2))
                    {
                        throw new UnionException(val1, val2);
                    }
                }
                return v1;
            }
            return v2;
        }

        @Override
        // Hmm
        public Optional<ISExp> copyValue(Optional<ISExp> v)
        {
            return v;
        }
    }

    private static void putSubs(DisjointMap<Symbol, Optional<ISExp>> subs, ImmutableMap<Symbol, ISExp> newSubs)
    {
        for (Map.Entry<Symbol, ISExp> entry : newSubs.entrySet())
        {
            subs.makeSet(entry.getKey());
            if(entry.getValue() instanceof Symbol)
            {
                Symbol value = (Symbol) entry.getValue();
                subs.makeSet(value);
                subs.union(entry.getKey(), value);
            }
            else
            {
                subs.set(entry.getKey(), Optional.of(entry.getValue()));
            }
        }
    }

    private void addBoundInValue(ISExp type, Set<ISExp> boundInValues)
    {
        type = find(type);
        if(type instanceof Symbol)
        {
            boundInValues.add(type);
        }
        else if(type instanceof IList)
        {
            // don't look inside labels, there's no types there
            if (type instanceof Cons && ((Cons) type).car.equals(makeString("label")))
            {
                return;
            }
            IList list = (IList) type;
            while(list != Nil.INSTANCE)
            {
                Cons cons = (Cons) list;
                addBoundInValue(cons.car, boundInValues);
                list = cons.cdr;
            }
        }
        else if(type instanceof net.minecraftforge.common.plon.AST.Map)
        {
            for(Map.Entry<? extends ISExp, ? extends ISExp> entry : ((net.minecraftforge.common.plon.AST.Map) type).value.entrySet())
            {
                addBoundInValue(entry.getKey(), boundInValues);
                addBoundInValue(entry.getValue(), boundInValues);
            }
        }
    }

    ISExp reify(ISExp type, java.util.Map<ISExp, ISExp> newVars, ImmutableSet<ISExp> boundVarTypes)
    {
        Set<ISExp> freeRootBuilder = Sets.newHashSet();
        Set<ISExp> boundInValues = Sets.newHashSet();
        for (Map.Entry<Symbol, Optional<ISExp>> entry : globalEnv.substitution)
        {
            Symbol symbol = globalEnv.substitution.find(entry.getKey()).get();
            if(!entry.getValue().isPresent())
            {
                freeRootBuilder.add(symbol);
            }
        }
        for(ISExp bound : boundVarTypes)
        {
            addBoundInValue(bound, boundInValues);
        }
        freeRootBuilder.removeAll(boundInValues);
        freeRootBuilder.removeAll(boundVarTypes);
        ImmutableSet<ISExp> freeRoots = ImmutableSet.copyOf(freeRootBuilder);
        ISExp newType = reify_do(type, newVars, freeRoots);
        Multimap<ISExp, ISExp> builder = HashMultimap.create();
        builder.putAll(globalEnv.lacks);
        for (Map.Entry<ISExp, ISExp> entry : globalEnv.lacks.entries())
        {
            ISExp key = find(entry.getKey());
            ISExp value = find(entry.getValue());
            if(newVars.containsKey(key))
            {
                key = newVars.get(key);
            }
            if(newVars.containsKey(value))
            {
                value = newVars.get(value);
            }
            builder.put(key, value);
        }
        globalEnv = new Environment(globalEnv.substitution, ImmutableMultimap.copyOf(builder));
        return newType;
    }

    private ISExp reify_do(ISExp type, java.util.Map<ISExp, ISExp> newVars, ImmutableSet<ISExp> freeRoots)
    {
        type = find(type);
        if(type instanceof Symbol)
        {
            if(freeRoots.contains(type))
            {
                if(!newVars.containsKey(type))
                {
                    ISExp newVar = getFreshVar();
                    newVars.put(type, newVar);
                }
            }
            if(newVars.containsKey(type))
            {
                return newVars.get(type);
            }
            return type;
        }
        if(type instanceof IList)
        {
            // don't look inside labels, there's no types there
            if(type instanceof Cons && ((Cons) type).car.equals(makeString("label")))
            {
                return type;
            }
            return reilist((IList) type, newVars, freeRoots);
        }
        if(type instanceof net.minecraftforge.common.plon.AST.Map)
        {
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            for(Map.Entry<? extends ISExp, ? extends ISExp> entry : ((net.minecraftforge.common.plon.AST.Map) type).value.entrySet())
            {
                builder.put(
                reify_do(entry.getKey(), newVars, freeRoots),
                reify_do(entry.getValue(), newVars, freeRoots)
                );
            }
        }
        else if(type == MNil.INSTANCE || type == Nil.INSTANCE || type instanceof StringAtom)
        {
            return type;
        }
        throw new IllegalStateException("Not a well-formed type: " + type);
    }

    private IList reilist(IList args, java.util.Map<ISExp, ISExp> newVars, ImmutableSet<ISExp> freeRoots)
    {
        if(args == Nil.INSTANCE)
        {
            return Nil.INSTANCE;
        }
        Cons cons = (Cons) args;
        return new Cons(reify_do(cons.car, newVars, freeRoots), reilist(cons.cdr, newVars, freeRoots));
    }

    private static class UnifyException extends Exception
    {
        public UnifyException(String message)
        {
            super(message);
        }
    }

    private static final class Environment
    {
        private final DisjointMap<Symbol, Optional<ISExp>> substitution; // row ~ row is actually here too
        //private final DisjointMap<Symbol, Optional<ISExp>> equals; // row ~ row
        private final ImmutableMultimap<ISExp, ISExp> lacks; // row -> label

        public Environment()
        {
            this(
                DisjointMap.create(UniqueOptionalValueOps.INSTANCE),
                //DisjointMap.create(ops),
                ImmutableMultimap.<ISExp, ISExp>of());
        }

        private Environment(DisjointMap<Symbol, Optional<ISExp>> substitution, /*DisjointMap<Symbol, Optional<ISExp>> equals, */ImmutableMultimap<ISExp, ISExp> lacks)
        {
            this.substitution = substitution;
            //this.equals = equals;
            this.lacks = lacks;
        }

        public Environment(ImmutableMap<Symbol, ISExp> substitution, /*ImmutableMap<Symbol, ISExp> equals, */ImmutableSet<Pair<ISExp, ISExp>> lacks)
        {
            this.substitution = DisjointMap.create(UniqueOptionalValueOps.INSTANCE);
            putSubs(this.substitution, substitution);
            //this.equals = DisjointMap.create(ops);
            //putSubs(this.equals, equals);
            ImmutableMultimap.Builder<ISExp, ISExp> builder = ImmutableMultimap.builder();
            for (Pair<ISExp, ISExp> pair : lacks)
            {
                builder.put(pair.getLeft(), pair.getRight());
            }
            this.lacks = builder.build();
        }

        public Environment(ImmutableMap<Symbol, ISExp> substitution)
        {
            this(substitution, /*ImmutableMap.<Symbol, ISExp>of(), */ImmutableSet.<Pair<ISExp, ISExp>>of());
        }

        Environment mergeWith(Environment env) throws UnifyException
        {
            DisjointMap<Symbol, Optional<ISExp>> newS = substitution.copy();
            for (Map.Entry<Symbol, Optional<ISExp>> entry : env.substitution)
            {
                Symbol root = env.substitution.find(entry.getKey()).get();
                newS.makeSet(entry.getKey());
                newS.makeSet(root);
                try
                {
                    newS.union(entry.getKey(), root);
                    newS.set(root, entry.getValue());
                }
                catch (UnionException e)
                {
                    throw new UnifyException("Couldn't make equivalent: " + typeToString(entry.getKey(), env) + " and " + typeToString(root, this));
                }
            }
            //DisjointMap<Symbol, Optional<ISExp>> newE = equals.copy();
            //newE.addAll(env.equals);
            Multimap<ISExp, ISExp> newLacks = HashMultimap.create();
            newLacks.putAll(lacks);
            newLacks.putAll(env.lacks);
            Environment newEnv = new Environment(newS, /*newE, */ImmutableMultimap.copyOf(newLacks));
            newEnv.checkLacks();
            return newEnv;
        }

        void checkLacks() throws UnifyException
        {
            for (Map.Entry<ISExp, ISExp> entry : lacks.entries())
            {
                checkLack(entry.getKey(), entry.getValue());
            }
        }

        void checkLack(ISExp row, ISExp label) throws UnifyException
        {
            row = find(row);
            if (row instanceof Cons)
            {
                if (length(row) == 1)
                {
                    // empty row lack everything
                    return;
                }
                Cons c1 = (Cons) row;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                Cons c4 = (Cons) c3.cdr;
                ISExp l2 = find(c2.car);
                if(label.equals(l2))
                {
                    throw new UnifyException("Unsatisfiable constraint: " + typeToString(row, this) + " lacks " + label);
                }
                checkLack(c4.car, label);
            }
            // otherwise can't check the constraint at this time
        }

        public ISExp find(ISExp exp)
        {
            if(exp instanceof Symbol)
            {
                Symbol var = (Symbol) exp;
                Optional<Symbol> root = substitution.find(var);
                if(root.isPresent())
                {
                    var = root.get();
                }
                Optional<Optional<ISExp>> value = substitution.get(var);
                if (value.isPresent() && value.get().isPresent())
                {
                    return value.get().get();
                }
                return var;
            }
            return exp;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Environment that = (Environment) o;
            return com.google.common.base.Objects.equal(substitution, that.substitution) &&
            Objects.equal(lacks, that.lacks);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(substitution, lacks);
        }
    }

    private static class EnvSet
    {
        private final ImmutableSet<Environment> evs;
        private final UnifyException exception;

        public EnvSet(Environment env)
        {
            this(ImmutableSet.of(env));
        }

        public EnvSet(ImmutableSet<Environment> evs, UnifyException exception)
        {
            this.evs = evs;
            this.exception = exception;
        }

        public EnvSet(ImmutableSet<Environment> evs)
        {
            this(evs, null);
        }

        public EnvSet(UnifyException exception)
        {
            this(ImmutableSet.<Environment>of(), exception);
        }

        public EnvSet mergeWithEnv(final Environment env) throws UnifyException
        {
            Set<Environment> set = Sets.newHashSet();
            for (Environment ev : evs)
            {
                set.add(ev.mergeWith(env));
            }
            return new EnvSet(ImmutableSet.copyOf(set));
        }

        /*public Iterator<ImmutableList<Environment>> iterator()
        {
            return new Iterator<ImmutableList<Environment>>()
            {
                private ImmutableList<Environment> last = null;

                private final List<Iterator<Environment>> is = Lists.transform(evProduct, new Function<ImmutableSet<Environment>, Iterator<Environment>>()
                {
                    public Iterator<Environment> apply(ImmutableSet<Environment> env)
                    {
                        return env.iterator();
                    }
                });

                public boolean hasNext()
                {
                    return Iterables.any(is, new Predicate<Iterator>() {
                        public boolean apply(Iterator input)
                        {
                            return input.hasNext();
                        }
                    });
                }

                @Override
                public ImmutableList<Environment> next()
                {
                    if(last == null)
                    {
                        last = ImmutableList.<Environment>of(Iterables.<Iterator<Environment>, Environment>transform(is, new Function<Iterator<Environment>, Environment>()
                        {
                            public Environment apply(Iterator<Environment> input)
                            {
                                return input.next();
                            }
                        }));
                        return last;
                    }
                    else
                    {
                        ImmutableList.Builder<Environment> builder = ImmutableList.builder();
                        boolean incremented = false;
                        for (int i = 0; i < last.size(); i++)
                        {
                            if(!incremented && !is.get(i).hasNext())
                            {
                                Iterator<Environment> it = evProduct.get(i).iterator();
                                is.set(i, it);
                                builder.add(it.next());
                                incremented = true;
                            }
                            else
                            {
                                builder.add(last.get(i));
                            }
                        }
                        last = builder.build();
                        return last;
                    }
                }

                @Override
                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
        }*/
    }

    // flatMap
    private EnvSet unify(final ISExp first, final ISExp second, EnvSet evs)
    {
        ImmutableSet.Builder<Environment> builder = ImmutableSet.builder();
        UnifyException exception = null;
        for (Environment env : evs.evs)
        {
            try
            {
                EnvSet envSet = unify(first, second, env);
                if(envSet.evs.isEmpty())
                {
                    exception = envSet.exception;
                }
                else for(Environment newEnv : envSet.evs)
                {
                    builder.add(newEnv);
                }
            }
            catch (UnifyException e)
            {
                exception = e;
            }
        }
        ImmutableSet<Environment> set = builder.build();
        return new EnvSet(set, exception);
    }

    private EnvSet unify(ISExp first, ISExp second, Environment env) throws UnifyException
    {
        final ISExp firstLink = env.find(first);
        final ISExp secondLink = env.find(second);
        if (firstLink instanceof Symbol)
        {
            return union((Symbol) firstLink, second, env);
        }
        else if (secondLink instanceof Symbol)
        {
            return union((Symbol) secondLink, first, env);
        }
        /*else if(firstLink instanceof Cons && length(firstLink) == 3 && secondLink instanceof IMap)
        {
            Cons c1 = (Cons) firstLink;
            Cons c2 = (Cons) c1.cdr;
            Cons c3 = (Cons) c2.cdr;
            if(c1.car.equals(makeString("->")))
            {
                ISExp l = getFreshVar();
                unify(c3.car, PrimTypes.String.type);
                unify(c2.car, new Map())
                return;
            }
        }*/
        // type constructor
        else if (firstLink instanceof Cons && secondLink instanceof Cons)
        {
            Cons firstCons = (Cons) firstLink;
            Cons secondCons = (Cons) secondLink;
            if (firstCons.car instanceof StringAtom && firstCons.car.equals(secondCons.car))
            {
                String name = ((StringAtom) firstCons.car).value;
                if (name.equals("{}"))
                {
                    // record unification
                    // {} == {}
                    if (length(firstCons.cdr) == 1 && length(secondCons.cdr) == 1)
                    {
                        return new EnvSet(env);
                    }
                    if (length(firstCons) == 4)
                    {
                        Cons c1 = (Cons) firstCons.cdr;
                        Cons c2 = (Cons) c1.cdr;
                        Cons c3 = (Cons) c2.cdr;
                        ISExp lLink = env.find(c1.car);
                        return unifyRows(lLink, c2.car, c3.car, secondLink, env);
                        /*if(!(lLink instanceof Symbol))
                        {
                            ISExp r2 = insert(lLink, c2.car, secondLink);
                            unify(c3.car, r2);
                            return;
                        }*/
                    }
                    /*if(length(secondCons) == 4)
                    {
                        Cons c1 = (Cons) secondCons.cdr;
                        Cons c2 = (Cons) c1.cdr;
                        Cons c3 = (Cons) c2.cdr;
                        ISExp lLink = find(c1.car);
                        if (!(lLink instanceof Symbol))
                        {
                            ISExp r2 = insert(lLink, c2.car, firstLink);
                            unify(c3.car, r2);
                            return;
                        }
                    }*/
                }
                else if (name.equals("label"))
                {
                    if (firstCons.cdr.equals(secondCons.cdr))
                    {
                        return new EnvSet(env);
                    }
                }
                else // normal constructor
                {
                    IList firstArgs = firstCons.cdr;
                    IList secondArgs = secondCons.cdr;
                    ImmutableSet<Environment> ret = ImmutableSet.of(env);
                    UnifyException exception = null;
                    while (firstArgs != Nil.INSTANCE && secondArgs != Nil.INSTANCE)
                    {
                        Cons c1 = (Cons) firstArgs;
                        Cons c2 = (Cons) secondArgs;
                        ImmutableSet<Environment> arg = unify(c1.car, c2.car, env).evs;
                        ImmutableSet.Builder<Environment> builder = ImmutableSet.builder();
                        for (List<Environment> pair : Sets.cartesianProduct(ImmutableList.of(ret, arg)))
                        {
                            try
                            {
                                builder.add(pair.get(0).mergeWith(pair.get(1)));
                            }
                            catch(UnifyException e) // couldn't merge
                            {
                                exception = e;
                            }
                        }
                        ret = builder.build();
                        firstArgs = c1.cdr;
                        secondArgs = c2.cdr;
                    }
                    if (firstArgs == secondArgs) // == Nil.INSTANCE
                    {
                        return new EnvSet(ret, exception);
                    }
                }
            }
        }
        else if (firstLink.equals(secondLink))
        {
            return new EnvSet(env);
        }
        throw new UnifyException("Type error: can't unify " + typeToString(firstLink, env) + " and " + typeToString(secondLink, env));
    }

    /*private ImmutableSet<Environment> mergeSubs(ImmutableSet<Environment> evs, final Environment newEnv)
    {
        return ImmutableSet.copyOf(Collections2.transform(evs, new Function<ImmutableMap<Symbol, ISExp>, ImmutableMap<Symbol, ISExp>>()
        {
            public ImmutableMap<Symbol, ISExp> apply(ImmutableMap<Symbol, ISExp> input)
            {
                java.util.Map<Symbol, ISExp> map = Maps.newHashMap(input);
                for(java.util.Map.Entry<Symbol, ISExp> entry : newEnv.entrySet())
                {
                    if(map.containsKey(entry.getKey()))
                    {
                        if(!map.get(entry.getKey()).equals(entry.getValue()))
                        {
                            throw new IllegalStateException("Conflicting mappings: " + entry.getKey() + " -> " + map.get(entry.getKey()) + ", " + entry.getValue());
                        }
                    }
                    else
                    {
                        map.put(entry.getKey(), entry.getValue());
                    }
                }
                return ImmutableMap.copyOf(map);
            }
        }));
    }*/

    private EnvSet unifyRows(ISExp l, ISExp t, ISExp r, ISExp s, Environment env)
    {
        ImmutableSet.Builder<Environment> builder = ImmutableSet.builder();
        ImmutableSet<Pair<Environment, ISExp>> inserters = null;
        try
        {
            inserters = insert(l, t, s, env);
        }
        catch (UnifyException e)
        {
            return new EnvSet(e);
        }
        UnifyException exception = null;
        for (final Pair<Environment, ISExp> inserter : inserters)
        {
            ISExp s_l = inserter.getRight();
            try
            {
                Environment newEnv = env.mergeWith(inserter.getLeft());
                EnvSet unifiers = unify(r, s_l, newEnv);
                builder.addAll(unifiers.evs);
            }
            catch (UnifyException e)
            {
                exception = e;
            }
        }
        ImmutableSet<Environment> evs = builder.build();
        return new EnvSet(evs, exception);
    }

    // mostly done?
    private ImmutableSet<Pair<Environment, ISExp>> insert(final ISExp l, final ISExp t, ISExp r, Environment env) throws UnifyException
    {
        ISExp rLink = env.find(r);
        if(rLink instanceof Symbol)
        {
            final Symbol A = (Symbol) rLink;
            final ISExp B = getFreshVar();
            if(occurs(A, t))
            {
                return ImmutableSet.of();
            }
            final ISExp ra = makeRow(l, t, B);
            return ImmutableSet.of(Pair.of(env.mergeWith(new Environment(ImmutableMap.of(A, ra))), B));
        }
        if(length(rLink) == 1)
        {
            throw new UnifyException("Can't insert label " + l + " into an empty row");
        }
        Cons c1 = (Cons) rLink;
        Cons c2 = (Cons) c1.cdr;
        Cons c3 = (Cons) c2.cdr;
        Cons c4 = (Cons) c3.cdr;
        ISExp l1 = c2.car;
        ISExp t1 = c3.car;
        ISExp r1 = c4.car;
        ImmutableSet<Pair<Environment, ISExp>> inserters;
        UnifyException exception = null;
        try
        {
            inserters = insert(l, t, r1, env);
        }
        catch(UnifyException e)
        {
            inserters = ImmutableSet.of();
            exception = e;
        }
        EnvSet fields;
        try
        {
            fields = unifyFields(l, l1, t, t1, env);
        }
        catch(UnifyException e)
        {
            fields = new EnvSet(e);
        }
        ImmutableSet.Builder<Pair<Environment, ISExp>> builder = ImmutableSet.builder();
        for (Environment field : fields.evs)
        {
            builder.add(Pair.of(field, r1));
        }
        for (Pair<Environment, ISExp> inserter : inserters)
        {
            builder.add(Pair.of(inserter.getLeft(), makeRow(l1, t1, inserter.getRight())));
        }
        ImmutableSet<Pair<Environment, ISExp>> set = builder.build();
        if(set.isEmpty())
        {
            // FIXME: which of these should be thrown?
            if(exception != null)
            {
                throw exception;
            }
            throw fields.exception;
        }
        return set;
    }

    private EnvSet unifyFields(ISExp l1, ISExp l2, ISExp t1, ISExp t2, Environment env) throws UnifyException
    {
        if(l1 instanceof Symbol)
        {
            return unify(t1, t2, env.mergeWith(new Environment(ImmutableMap.of((Symbol) l1, l2))));
        }
        if(l2 instanceof Symbol)
        {
            return unify(t1, t2, env.mergeWith(new Environment(ImmutableMap.of((Symbol) l2, l1))));
        }
        else if(l1.equals(l2)) // TODO: good enough equality?
        {
            return unify(t1, t2, env);
        }
        throw new UnifyException("Couldn't unify labels: " + l1 + " and " + l2);
    }

    /*private ISExp insert(ISExp l, ISExp t, ISExp r)
    {
        ISExp rLink = find(r);
        if(rLink instanceof Symbol)
        {
            Symbol type = (Symbol) rLink;
            if(occurs(type, t))
            {
                ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
                throw new IllegalStateException("recursive map type: " + typeToString(type, typeMap) + " inside " + typeToString(t, typeMap));
            }
            ISExp r2 = getFreshVar();
            union(type, makeRow(l, t, r2));
            return r2;
        }
        else if(rLink instanceof Cons && ((Cons) rLink).car.equals(makeString("{}")))
        {
            if(length(rLink) == 4) // not {}
            {
                Cons c1 = (Cons) rLink;
                Cons c2 = (Cons) c1.cdr;
                Cons c3 = (Cons) c2.cdr;
                Cons c4 = (Cons) c3.cdr;
                // TODO: polymorphic labels?
                ISExp l1Link = find(l);
                ISExp l2Link = find(c2.car);
                // FIXME: if label is a symbol, there should be a constraint added
                // inHead
                if(l1Link instanceof Symbol)
                {
                    union((Symbol) l1Link, l2Link);
                    unify(c3.car, t);
                }
                else if(l2Link instanceof Symbol)
                {
                    union((Symbol) l2Link, l1Link);
                    unify(c3.car, t);
                }
                else if(c2.car.equals(l))
                {
                    unify(c3.car, t);
                }
                else
                {
                    // inTail
                    return makeRow(c2.car, c3.car, insert(l, t, c4.car));
                }
                return c4.car;
            }
        }
        ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
        throw new IllegalStateException("Type error: can't insert " + typeToString(l, typeMap) + " : " + typeToString(t, typeMap) + " into " + typeToString(rLink, typeMap));
    }*/

    // TODO: rethink this and disjoint set
    /*ISExp find(ISExp type)
    {
        if(type instanceof Symbol)
        {
            Symbol var = (Symbol) type;
            if (boundVars.containsKey(var))
            {
                ISExp to = boundVars.get(var);
                if(!type.equals(to))
                {
                    ISExp root = find(to);
                    if (to != root)
                    {
                        boundVars.put(var, root);
                    }
                    return root;
                }
            }
        }
        return type;
    }*/

    private boolean occurs(Symbol type, ISExp other)
    {
        ISExp link = globalEnv.find(other);
        if(link instanceof Symbol)
        {
            // FIXME: ==?
            return type.equals(link);
        }
        else if(link instanceof IList)
        {
            IList list = (IList) link;
            while(list != Nil.INSTANCE)
            {
                Cons cons = (Cons) list;
                if(occurs(type, cons.car))
                {
                    return true;
                }
                list = cons.cdr;
            }
        }
        return false;
    }

    private EnvSet union(final Symbol type, final ISExp other, Environment env) throws UnifyException
    {
        if(!type.equals(other))
        {
            if(!occurs(type, other))
            {
                return new EnvSet(env.mergeWith(new Environment(ImmutableMap.of(type, other))));
                // manually creating it cause it might be complex
                /*union.makeSet(other);
                union.union(type, other);
                // TODO: check
                boundVars.put(type, other);*/
            }
            return new EnvSet(new UnifyException("Occurs: " + type + " in " + other));
            //ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
            //throw new IllegalStateException("recursive type: " + typeToString(type, typeMap) + " = " + typeToString(other, typeMap));
        }
        return new EnvSet(env);
    }

    /*public boolean isBound(ISExp symbol)
    {
        // TODO: check
        return boundVars.containsKey(symbol);
    }*/

    /*public ImmutableMap<ISExp, ISExp> buildTypeMap()
    {
        ImmutableMap<ISExp, ImmutableSet<ISExp>> map = union.toMap();
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        for (ISExp from : map.keySet())
        {
            ISExp to = null;
            // can be faster since values have a lot of repeats
            for(ISExp candidate : map.get(from))
            {
                if(!(candidate instanceof Symbol))
                {
                    to = candidate;
                    break;
                }
            }
            if(to != null)
            {
                builder.put(from, to);
            }
        }
        return builder.build();
    }*/

    /*public ImmutableMap<Symbol, ISExp> buildResult()
    {
        ImmutableMap<ISExp, ISExp> typeMap = buildTypeMap();
        ImmutableMap.Builder<Symbol, ISExp> builder = ImmutableMap.builder();
        for(java.util.Map.Entry<Symbol, ISExp> entry : globalEnv.entrySet())
        {
            ISExp type = entry.getValue();
            if(typeMap.containsKey(type))
            {
                type = typeMap.get(type);
            }
            builder.put(entry.getKey(), type);
        }
        return builder.build();
    }*/

    public String typeToString(ISExp type)
    {
        return typeToString(type, globalEnv);
    }

    private static String rowToString(ISExp row, Environment env, boolean first)
    {
        row = env.find(row);
        if(row instanceof Symbol)
        {
            return " | " + typeToString(row, env);
        }
        if(length(row) == 1) return "";
        Cons c1 = (Cons) row;
        Cons c2 = (Cons) c1.cdr;
        Cons c3 = (Cons) c2.cdr;
        Cons c4 = (Cons) c3.cdr;
        String r = "";
        if(!first)
        {
            r = ", ";
        }
        return r + typeToString(c2.car, env) + " = " + typeToString(c3.car, env) + rowToString(c4.car, env, false);
    }

    public static String typeToString(ISExp type, Environment env)
    {
        type = env.find(type);
        if(type instanceof StringAtom)
        {
            return ((StringAtom) type).value;
        }
        else if(type instanceof Symbol)
        {
            return ((Symbol) type).value;
        }
        else if(type == Unbound.INSTANCE)
        {
            return "&unbound";
        }
        Cons cons = (Cons)type;
        if(cons.car.equals(makeString("{}")))
        {
            if(cons.cdr == Nil.INSTANCE)
            {
                return "{ }";
            }
            return "{ " + rowToString(cons, env, true) + " }";
        }
        else if(cons.car.equals(makeString("label")))
        {
            return "[label: " + ((Cons) cons.cdr).car + "]";
        }
        else if(cons.car.equals(makeString("*")))
        {
            return "Prod { " + rowToString(((Cons) cons.cdr).car, env, true) + " }";
        }
        else if(cons.car.equals(makeString("+")))
        {
            return "Sum { " + rowToString(((Cons) cons.cdr).car, env, true) + " }";
        }
        else if(cons.car.equals(makeString("->")))
        {
            // FIXME
            /*if (length(type) == 2)
            {
                r = typeToString(cons.car, env) + " -> ";
                cons = (Cons) cons.cdr;
            }
            else
            {
                r = "(";
                while (cons.cdr != Nil.INSTANCE)
                {
                    r += typeToString(cons.car, env);
                    cons = (Cons) cons.cdr;
                    if (cons.cdr != Nil.INSTANCE)
                    {
                        r += ", ";
                    }
                }
                r += ") -> ";
            }
            if (cons.car instanceof IList)
            {
                r += "(" + typeToString(cons.car, env) + ")";
            }
            else
            {
                r += typeToString(cons.car, env);
            }*/
            Cons c1 = (Cons) cons.cdr;
            IList args = c1.cdr;
            String r = "((";
            while(args != Nil.INSTANCE)
            {
                Cons c2 = (Cons) args;
                r += typeToString(c2.car, env);
                if(c2.cdr != Nil.INSTANCE)
                {
                    r += ", ";
                }
                args = c2.cdr;
            }
            r += ") -> " + typeToString(c1.car, env) + ")";
            return r;
        }
        throw new UnsupportedOperationException();
    }

    /*public void copyConstraints(ISExp type, ISExp newVar)
    {
        constraints.putAll(newVar, constraints.get(type));
    }*/
}
